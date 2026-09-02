package com.uaiou.routing.service;

import com.uaiou.routing.RoutingService.Point;
import com.uaiou.routing.RoutingService.Route;
import com.uaiou.routing.RoutingService.Step;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * RF-25.9 — cache de trajetos. Guardar o resultado é permitido pela licença do provedor (RF-25.2).
 *
 * <p>Mesma disciplina do {@link com.uaiou.presence.service.PresenceCache}: <strong>cache, nunca
 * fonte de verdade</strong>. Redis fora do ar vira "não sei" e o chamador volta ao provedor; nenhum
 * método daqui lança.
 *
 * <p>A chave é responsabilidade do chamador porque é ele que conhece a invariância do que está
 * guardando — ver {@link com.uaiou.orders.service.OrderRouteService}.
 */
@Component
public class RouteCache {

  private static final Logger log = LoggerFactory.getLogger(RouteCache.class);

  private final StringRedisTemplate redis;

  public RouteCache(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public Optional<Route> find(String key) {
    try {
      String value = redis.opsForValue().get(key);
      return value == null ? Optional.empty() : deserialize(value);
    } catch (RuntimeException e) {
      log.warn("Cache de rotas indisponível na leitura — seguindo para o provedor.", e);
      return Optional.empty();
    }
  }

  public void save(String key, Route route, Duration ttl) {
    try {
      redis.opsForValue().set(key, serialize(route), ttl);
    } catch (RuntimeException e) {
      log.warn("Cache de rotas indisponível na escrita — a rota vale só para esta resposta.", e);
    }
  }

  /**
   * Arredonda a coordenada de quem pergunta para a chave: sem isso cada metro andado seria uma
   * chave nova e o cache nunca acertaria.
   */
  public static String arredondar(BigDecimal valor, int casas) {
    return valor.setScale(casas, RoundingMode.HALF_UP).toPlainString();
  }

  /**
   * {@code distanciaKm;segundos;lat,lng|...;texto,metros,segundos,indice|...}
   *
   * <p>O texto da instrução vai <strong>percent-encoded</strong>: vem do provedor, em português,
   * com vírgula e acento — inseri-lo cru num formato separado por vírgula seria um bug esperando a
   * primeira rua com nome composto.
   */
  private String serialize(Route route) {
    StringBuilder geometria = new StringBuilder();
    for (Point ponto : route.geometry()) {
      if (!geometria.isEmpty()) {
        geometria.append('|');
      }
      geometria.append(ponto.lat()).append(',').append(ponto.lng());
    }

    StringBuilder passos = new StringBuilder();
    for (Step passo : route.steps()) {
      if (!passos.isEmpty()) {
        passos.append('|');
      }
      passos
          .append(URLEncoder.encode(passo.instruction(), StandardCharsets.UTF_8))
          .append(',')
          .append(passo.distanceMeters())
          .append(',')
          .append(passo.durationSeconds())
          .append(',')
          .append(passo.pointIndex());
    }

    return route.distanceKm() + ";" + route.duration().toSeconds() + ";" + geometria + ";" + passos;
  }

  private Optional<Route> deserialize(String value) {
    try {
      String[] parts = value.split(";", -1);
      if (parts.length != 4) {
        return Optional.empty();
      }

      List<Point> geometria = new ArrayList<>();
      if (!parts[2].isEmpty()) {
        for (String par : parts[2].split("\\|")) {
          String[] coordenada = par.split(",");
          geometria.add(new Point(new BigDecimal(coordenada[0]), new BigDecimal(coordenada[1])));
        }
      }

      List<Step> passos = new ArrayList<>();
      if (!parts[3].isEmpty()) {
        for (String bruto : parts[3].split("\\|")) {
          String[] campos = bruto.split(",");
          if (campos.length != 4) {
            continue;
          }
          passos.add(
              new Step(
                  URLDecoder.decode(campos[0], StandardCharsets.UTF_8),
                  Integer.parseInt(campos[1]),
                  Integer.parseInt(campos[2]),
                  Integer.parseInt(campos[3])));
        }
      }

      return Optional.of(
          new Route(
              new BigDecimal(parts[0]),
              Duration.ofSeconds(Long.parseLong(parts[1])),
              List.copyOf(geometria),
              List.copyOf(passos)));
    } catch (RuntimeException e) {
      // Formato inesperado (deploy antigo, chave escrita à mão) é acerto perdido, nunca erro.
      log.warn("Entrada de rota ilegível no cache — tratada como ausente.", e);
      return Optional.empty();
    }
  }
}
