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
 * RF-25.2 — implementação Geoapify. A licença do provedor é derivada do OpenStreetMap e
 * <strong>permite armazenar o resultado</strong>, que é o que torna o cache de RF-25.9 e a trilha
 * de auditoria possíveis sem violar termo de uso — diferente dos provedores proprietários.
 *
 * <p>Toda falha é engolida e vira {@link Optional#empty()} (RF-25.10). O registro (RF-25.11) sai
 * aqui com o resultado da chamada real; o acerto de cache é registrado por quem consulta o cache.
 */
public class GeoapifyRoutingService implements RoutingService {

  private static final Logger log = LoggerFactory.getLogger(GeoapifyRoutingService.class);

  private final RestClient restClient;
  private final RoutingProperties properties;

  public GeoapifyRoutingService(RestClient restClient, RoutingProperties properties) {
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
      GeoapifyResponse resposta =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/v1/routing")
                          .queryParam("waypoints", waypoints(waypoints))
                          .queryParam("mode", properties.mode())
                          // Instruções em português — o entregador lê a curva, não traduz.
                          .queryParam("lang", properties.language())
                          .queryParam("details", "instruction_details")
                          .queryParam("format", "geojson")
                          // A chave vai na query porque é o único esquema que o provedor aceita —
                          // por isso ela nunca entra em log de URL (ver catch abaixo, que registra
                          // só a exceção) nem é ecoada em resposta (RF-25.3, critério 7).
                          .queryParam("apiKey", properties.apiKey())
                          .build())
              .retrieve()
              .body(GeoapifyResponse.class);

      Optional<Route> rota = converter(resposta, waypoints.size());
      log.info(
          "routing.provider outcome={} waypoints={} elapsedMs={}",
          rota.isPresent() ? "OK" : "EMPTY",
          waypoints.size(),
          decorridoMs(inicio));
      return rota;
    } catch (RuntimeException e) {
      // Provedor fora do ar, cota estourada, tempo limite, corpo inesperado: tudo é "sem rota".
      log.warn(
          "routing.provider outcome=FAILURE elapsedMs={} — seguindo sem rota.",
          decorridoMs(inicio),
          e);
      return Optional.empty();
    }
  }

  /** Geoapify espera {@code lat,long|lat,long|...} — ao contrário do GeoJSON que devolve. */
  private String waypoints(List<Point> pontos) {
    return pontos.stream()
        .map(ponto -> ponto.lat() + "," + ponto.lng())
        .collect(Collectors.joining("|"));
  }

  private Optional<Route> converter(GeoapifyResponse resposta, int quantidadeDeWaypoints) {
    if (resposta == null || resposta.features() == null || resposta.features().isEmpty()) {
      return Optional.empty();
    }
    Feature feature = resposta.features().getFirst();
    if (feature.properties() == null || feature.properties().distance() == null) {
      return Optional.empty();
    }

    List<List<Point>> pernas =
        feature.geometry() == null
            ? List.of()
            : coordenadasPorPerna(feature.geometry().coordinates());

    List<Point> geometria = pernas.stream().flatMap(List::stream).toList();
    if (geometria.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(
        new Route(
            BigDecimal.valueOf(feature.properties().distance() / 1000.0)
                .setScale(1, RoundingMode.HALF_UP),
            Duration.ofSeconds(
                feature.properties().time() == null ? 0 : Math.round(feature.properties().time())),
            geometria,
            instrucoes(feature.properties().legs(), pernas),
            paradas(pernas, quantidadeDeWaypoints)));
  }

  /**
   * Onde cada perna termina na geometria emendada — é o que permite ao cliente separar "até a loja"
   * de "da loja até a entrega" no ponto exato, em vez de adivinhar pelo meio do traçado. Só vale
   * quando o provedor devolveu uma perna por trecho; do contrário, vazio.
   */
  private List<Integer> paradas(List<List<Point>> pernas, int quantidadeDeWaypoints) {
    if (pernas.size() != quantidadeDeWaypoints - 1 || pernas.size() < 2) {
      return List.of();
    }
    List<Integer> indices = new ArrayList<>();
    int acumulado = 0;
    for (int i = 0; i < pernas.size() - 1; i++) {
      acumulado += pernas.get(i).size();
      indices.add(acumulado - 1);
    }
    return List.copyOf(indices);
  }

  /**
   * Os índices de cada instrução são <strong>relativos à perna</strong> do provedor, e a geometria
   * que o cliente recebe é uma só, contínua. O deslocamento acumulado aqui é o que mantém "vire à
   * direita" apontando para o ponto certo depois da emenda — sem isso, toda instrução da segunda
   * metade do trajeto cairia no lugar errado.
   */
  private List<Step> instrucoes(List<Leg> legs, List<List<Point>> pernas) {
    if (legs == null || legs.isEmpty()) {
      return List.of();
    }

    List<Step> passos = new ArrayList<>();
    int deslocamento = 0;
    for (int i = 0; i < legs.size(); i++) {
      Leg leg = legs.get(i);
      if (leg != null && leg.steps() != null) {
        for (GeoapifyStep passo : leg.steps()) {
          if (passo == null || passo.instruction() == null || passo.instruction().text() == null) {
            continue;
          }
          passos.add(
              new Step(
                  passo.instruction().text(),
                  passo.distance() == null ? 0 : (int) Math.round(passo.distance()),
                  passo.time() == null ? 0 : (int) Math.round(passo.time()),
                  deslocamento + (passo.fromIndex() == null ? 0 : passo.fromIndex())));
        }
      }
      if (i < pernas.size()) {
        deslocamento += pernas.get(i).size();
      }
    }
    return List.copyOf(passos);
  }

  /**
   * O provedor devolve {@code LineString} (uma lista de pontos) ou {@code MultiLineString} (uma
   * lista por perna) conforme o número de waypoints. Em vez de amarrar o parser a uma das duas
   * formas — e quebrar no dia em que o trajeto tiver uma parada a menos —, a profundidade é medida:
   * lista de números é ponto, lista de pontos é perna.
   *
   * <p>GeoJSON é {@code [longitude, latitude]}; a inversão para {@code lat,lng} acontece aqui, uma
   * vez, e não em cada consumidor.
   */
  private List<List<Point>> coordenadasPorPerna(Object node) {
    if (!(node instanceof List<?> lista) || lista.isEmpty()) {
      return List.of();
    }
    if (ehPonto(lista.getFirst())) {
      return List.of(pontos(lista));
    }
    List<List<Point>> pernas = new ArrayList<>();
    for (Object filho : lista) {
      if (filho instanceof List<?> sub && !sub.isEmpty() && ehPonto(sub.getFirst())) {
        pernas.add(pontos(sub));
      }
    }
    return List.copyOf(pernas);
  }

  private boolean ehPonto(Object candidato) {
    return candidato instanceof List<?> par && par.size() >= 2 && par.getFirst() instanceof Number;
  }

  private List<Point> pontos(List<?> bruto) {
    List<Point> pontos = new ArrayList<>(bruto.size());
    for (Object item : bruto) {
      if (item instanceof List<?> par
          && par.size() >= 2
          && par.get(0) instanceof Number lng
          && par.get(1) instanceof Number lat) {
        pontos.add(
            new Point(
                BigDecimal.valueOf(lat.doubleValue()), BigDecimal.valueOf(lng.doubleValue())));
      }
    }
    return List.copyOf(pontos);
  }

  private long decorridoMs(long inicioNanos) {
    return (System.nanoTime() - inicioNanos) / 1_000_000;
  }

  /** Recorte mínimo do GeoJSON do provedor — o resto do payload é ignorado de propósito. */
  record GeoapifyResponse(List<Feature> features) {}

  record Feature(Properties properties, Geometry geometry) {}

  record Properties(Double distance, Double time, List<Leg> legs) {}

  record Leg(Double distance, Double time, List<GeoapifyStep> steps) {}

  record GeoapifyStep(
      Double distance,
      Double time,
      @com.fasterxml.jackson.annotation.JsonProperty("from_index") Integer fromIndex,
      Instruction instruction) {}

  record Instruction(String text) {}

  record Geometry(String type, Object coordinates) {}
}
