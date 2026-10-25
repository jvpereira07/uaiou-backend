package com.uaiou.routing.service;

import com.uaiou.routing.RoutingService;
import com.uaiou.routing.config.RoutingProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Implementação OSRM (Open Source Routing Machine), hospedado por nós sobre o mapa do
 * OpenStreetMap. Sem chave e sem cota por chamada — o que faz a matriz de {@link #matrix} caber no
 * orçamento — e única das implementações que a oferece.
 *
 * <p>Mesmo contrato da Geoapify: toda falha vira {@link Optional#empty()} (RF-25.10). O OSRM não
 * devolve texto de instrução, só a manobra; o português é montado aqui ({@link #instrucao}).
 */
public class OsrmRoutingService implements RoutingService {

  private static final Logger log = LoggerFactory.getLogger(OsrmRoutingService.class);

  /** O servidor ignora o perfil da URL (vale o do pré-processamento), mas a rota exige um. */
  private static final String PERFIL_PADRAO = "driving";

  private final RestClient restClient;
  private final RoutingProperties properties;

  public OsrmRoutingService(RestClient restClient, RoutingProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  @Override
  public Optional<Route> route(List<Point> waypoints) {
    if (waypoints == null || waypoints.size() < 2) {
      return Optional.empty();
    }

    long inicio = System.nanoTime();
    try {
      // Coordenadas no caminho literal, não como variável de URI: a variável seria codificada
      // estritamente e o ";" entre pontos viraria %3B.
      OsrmRouteResponse resposta =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/route/v1/" + perfil() + "/" + coordenadas(waypoints))
                          .queryParam("overview", "full")
                          .queryParam("geometries", "geojson")
                          .queryParam("steps", "true")
                          .build())
              .retrieve()
              .body(OsrmRouteResponse.class);

      Optional<Route> rota = converter(resposta, waypoints.size());
      log.info(
          "routing.provider provider=osrm outcome={} waypoints={} elapsedMs={}",
          rota.isPresent() ? "OK" : "EMPTY",
          waypoints.size(),
          decorridoMs(inicio));
      return rota;
    } catch (RuntimeException e) {
      log.warn(
          "routing.provider provider=osrm outcome=FAILURE elapsedMs={} — seguindo sem rota.",
          decorridoMs(inicio),
          e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Matrix> matrix(List<Point> points) {
    if (points == null || points.size() < 2) {
      return Optional.empty();
    }

    long inicio = System.nanoTime();
    try {
      OsrmTableResponse resposta =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/table/v1/" + perfil() + "/" + coordenadas(points))
                          .queryParam("annotations", "duration,distance")
                          .build())
              .retrieve()
              .body(OsrmTableResponse.class);

      Optional<Matrix> matriz = converterMatriz(resposta, points.size());
      log.info(
          "routing.matrix provider=osrm outcome={} points={} elapsedMs={}",
          matriz.isPresent() ? "OK" : "EMPTY",
          points.size(),
          decorridoMs(inicio));
      return matriz;
    } catch (RuntimeException e) {
      log.warn(
          "routing.matrix provider=osrm outcome=FAILURE elapsedMs={} — seguindo sem matriz.",
          decorridoMs(inicio),
          e);
      return Optional.empty();
    }
  }

  private String perfil() {
    String modo = properties.mode();
    return modo == null || modo.isBlank() || "motorcycle".equalsIgnoreCase(modo)
        ? PERFIL_PADRAO
        : modo;
  }

  /** OSRM espera {@code lng,lat;lng,lat;...} — ordem GeoJSON, ao contrário da Geoapify. */
  private String coordenadas(List<Point> pontos) {
    return pontos.stream()
        .map(ponto -> ponto.lng().toPlainString() + "," + ponto.lat().toPlainString())
        .collect(Collectors.joining(";"));
  }

  private Optional<Route> converter(OsrmRouteResponse resposta, int quantidadeDeWaypoints) {
    if (resposta == null
        || !"Ok".equals(resposta.code())
        || resposta.routes() == null
        || resposta.routes().isEmpty()) {
      return Optional.empty();
    }
    OsrmRoute rota = resposta.routes().getFirst();
    if (rota.distance() == null || rota.geometry() == null) {
      return Optional.empty();
    }
    List<Point> geometria = pontos(rota.geometry().coordinates());
    if (geometria.isEmpty()) {
      return Optional.empty();
    }

    List<Step> passos = new ArrayList<>();
    List<Integer> paradas = new ArrayList<>();
    int cursor = 0;
    List<OsrmLeg> legs = rota.legs() == null ? List.of() : rota.legs();
    for (int i = 0; i < legs.size(); i++) {
      List<OsrmStep> steps = legs.get(i).steps() == null ? List.of() : legs.get(i).steps();
      for (OsrmStep passo : steps) {
        if (passo == null || passo.maneuver() == null) {
          continue;
        }
        cursor = indiceMaisProximo(geometria, passo.maneuver().location(), cursor);
        String tipo = passo.maneuver().type();
        if ("arrive".equals(tipo) && i < legs.size() - 1) {
          paradas.add(cursor);
        }
        passos.add(
            new Step(
                instrucao(passo),
                passo.distance() == null ? 0 : (int) Math.round(passo.distance()),
                passo.duration() == null ? 0 : (int) Math.round(passo.duration()),
                cursor));
      }
    }
    if (legs.size() != quantidadeDeWaypoints - 1 || paradas.size() != legs.size() - 1) {
      paradas.clear();
    }

    return Optional.of(
        new Route(
            BigDecimal.valueOf(rota.distance() / 1000.0).setScale(1, RoundingMode.HALF_UP),
            Duration.ofSeconds(rota.duration() == null ? 0 : Math.round(rota.duration())),
            geometria,
            List.copyOf(passos),
            List.copyOf(paradas)));
  }

  /**
   * O OSRM não diz em que índice da geometria cada manobra acontece, só a coordenada. A busca anda
   * só para frente a partir do último achado: um trajeto que passa duas vezes pela mesma esquina
   * (ida e volta à loja) não pode fazer a segunda instrução apontar para a primeira passagem.
   */
  private int indiceMaisProximo(List<Point> geometria, List<Double> local, int cursor) {
    if (local == null || local.size() < 2) {
      return cursor;
    }
    double lng = local.get(0);
    double lat = local.get(1);
    int melhor = cursor;
    double menor = Double.MAX_VALUE;
    for (int i = cursor; i < geometria.size(); i++) {
      Point p = geometria.get(i);
      double dLat = p.lat().doubleValue() - lat;
      double dLng = p.lng().doubleValue() - lng;
      double d = dLat * dLat + dLng * dLng;
      if (d < menor) {
        menor = d;
        melhor = i;
        if (d == 0) {
          break;
        }
      }
    }
    return melhor;
  }

  private Optional<Matrix> converterMatriz(OsrmTableResponse resposta, int n) {
    if (resposta == null || !"Ok".equals(resposta.code()) || resposta.durations() == null) {
      return Optional.empty();
    }
    int[][] tempos = tabela(resposta.durations(), n);
    int[][] distancias = resposta.distances() == null ? vazia(n) : tabela(resposta.distances(), n);
    return tempos == null || distancias == null
        ? Optional.empty()
        : Optional.of(new Matrix(tempos, distancias));
  }

  private int[][] tabela(List<List<Double>> bruto, int n) {
    if (bruto.size() != n) {
      return null;
    }
    int[][] tabela = new int[n][n];
    for (int i = 0; i < n; i++) {
      List<Double> linha = bruto.get(i);
      if (linha == null || linha.size() != n) {
        return null;
      }
      for (int j = 0; j < n; j++) {
        Double valor = linha.get(j);
        tabela[i][j] = valor == null ? Matrix.UNREACHABLE : (int) Math.round(valor);
      }
    }
    return tabela;
  }

  private int[][] vazia(int n) {
    int[][] tabela = new int[n][n];
    for (int[] linha : tabela) {
      java.util.Arrays.fill(linha, Matrix.UNREACHABLE);
    }
    return tabela;
  }

  /** RF-25.2 — instrução em português a partir da manobra crua do OSRM. */
  static String instrucao(OsrmStep passo) {
    OsrmManeuver m = passo.maneuver();
    String via = passo.name() == null || passo.name().isBlank() ? null : passo.name();
    String direcao = direcao(m.modifier());
    String tipo = m.type() == null ? "" : m.type();

    String base =
        switch (tipo) {
          case "depart" -> "Siga" + (direcao == null ? "" : " " + direcao);
          case "arrive" -> "Você chegou ao destino";
          case "roundabout", "rotary" ->
              m.exit() == null
                  ? "Entre na rotatória"
                  : "Na rotatória, pegue a " + m.exit() + "ª saída";
          case "merge" -> "Entre na via";
          case "on ramp" -> "Pegue o acesso";
          case "off ramp" -> "Pegue a saída";
          case "fork" -> "Na bifurcação, mantenha-se " + (direcao == null ? "em frente" : direcao);
          case "end of road" -> "No fim da via, vire " + (direcao == null ? "" : direcao);
          case "continue", "new name" -> "Continue" + (direcao == null ? "" : " " + direcao);
          default ->
              direcao == null
                  ? "Continue"
                  : ("uturn".equals(m.modifier()) ? "Faça o retorno" : "Vire " + direcao);
        };
    base = base.strip();
    if (via == null || "arrive".equals(tipo)) {
      return base;
    }
    return base + ("depart".equals(tipo) ? " pela " : " na ") + via;
  }

  private static String direcao(String modifier) {
    if (modifier == null) {
      return null;
    }
    return switch (modifier) {
      case "left" -> "à esquerda";
      case "right" -> "à direita";
      case "slight left" -> "levemente à esquerda";
      case "slight right" -> "levemente à direita";
      case "sharp left" -> "acentuadamente à esquerda";
      case "sharp right" -> "acentuadamente à direita";
      case "straight" -> "em frente";
      case "uturn" -> "em retorno";
      default -> null;
    };
  }

  private List<Point> pontos(List<List<Double>> bruto) {
    if (bruto == null) {
      return List.of();
    }
    List<Point> pontos = new ArrayList<>(bruto.size());
    for (List<Double> par : bruto) {
      if (par != null && par.size() >= 2 && par.get(0) != null && par.get(1) != null) {
        pontos.add(new Point(BigDecimal.valueOf(par.get(1)), BigDecimal.valueOf(par.get(0))));
      }
    }
    return List.copyOf(pontos);
  }

  private long decorridoMs(long inicioNanos) {
    return (System.nanoTime() - inicioNanos) / 1_000_000;
  }

  /** Recorte mínimo das respostas do OSRM — o resto do payload é ignorado de propósito. */
  record OsrmRouteResponse(String code, List<OsrmRoute> routes) {}

  record OsrmRoute(Double distance, Double duration, OsrmGeometry geometry, List<OsrmLeg> legs) {}

  record OsrmGeometry(List<List<Double>> coordinates) {}

  record OsrmLeg(List<OsrmStep> steps) {}

  record OsrmStep(Double distance, Double duration, String name, OsrmManeuver maneuver) {}

  record OsrmManeuver(String type, String modifier, List<Double> location, Integer exit) {}

  record OsrmTableResponse(
      String code, List<List<Double>> durations, List<List<Double>> distances) {}
}
