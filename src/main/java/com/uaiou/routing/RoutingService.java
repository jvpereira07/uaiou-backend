package com.uaiou.routing;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * RF-25.1 — trajeto real por via, atrás de uma interface própria. Mesmo desenho de {@link
 * com.uaiou.orders.service.GeocodingService}: o provedor é detalhe de implementação e trocá-lo não
 * pode tocar em nenhum chamador.
 *
 * <p><strong>Nenhum método lança.</strong> Provedor fora do ar, cota estourada, tempo limite ou
 * recurso desligado por falta de chave devolvem {@link Optional#empty()} — "não sei o trajeto" —,
 * nunca erro. É RF-25.10: a rota é informação a mais, não pode virar ponto único de falha novo no
 * ciclo de ver → aceitar → finalizar.
 */
public interface RoutingService {

  /**
   * Trajeto passando por {@code waypoints} <strong>na ordem dada</strong> — dois pontos é uma perna
   * simples; três é "de onde estou, passando pela loja, até o destino".
   *
   * @return {@link Optional#empty()} = sem rota disponível agora (RF-25.10), nunca exceção.
   */
  Optional<Route> route(List<Point> waypoints);

  record Point(BigDecimal lat, BigDecimal lng) {}

  /**
   * Uma instrução de curva do provedor, já em português (RF-25.2).
   *
   * @param pointIndex posição, dentro de {@link Route#geometry()}, onde a instrução acontece. É o
   *     que permite ao cliente saber qual passo está por vir sem refazer geometria: ele acha o
   *     ponto mais próximo de si e compara índices.
   */
  record Step(String instruction, int distanceMeters, int durationSeconds, int pointIndex) {}

  /**
   * @param distanceKm distância <strong>por via</strong> — nome distinto da distância em linha reta
   *     da vitrine (RF-25.8): as duas medidas coexistem e o cliente precisa saber qual é qual.
   * @param geometry traçado contínuo, na ordem em que se percorre.
   */
  /**
   * @param waypointIndices índice, na {@code geometry}, de cada parada intermediária (o ponto onde
   *     uma perna termina e a próxima começa). Vazio quando o provedor não separou as pernas.
   */
  record Route(
      BigDecimal distanceKm,
      Duration duration,
      List<Point> geometry,
      List<Step> steps,
      List<Integer> waypointIndices) {

    public Route(BigDecimal distanceKm, Duration duration, List<Point> geometry, List<Step> steps) {
      this(distanceKm, duration, geometry, steps, List.of());
    }
  }
}
