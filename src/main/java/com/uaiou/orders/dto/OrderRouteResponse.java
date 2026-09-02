package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta de {@code GET /orders/{id}/route} (RF-25.6).
 *
 * <p><strong>Uma rota só</strong>, do ponto do entregador até o destino, passando pelo
 * estabelecimento (decisão do dono, 2026-08-16 — ver "Riscos e decisões" de T-25). O desenho
 * anterior devolvia duas pernas independentes; o custo dessa troca está registrado lá.
 *
 * <p>RF-25.8 — as duas medidas coexistem e a resposta diz qual é qual: {@code
 * straightLineDistanceKm} é a mesma linha reta do {@code distanceKm} da vitrine (RF-11.7), {@code
 * roadDistanceKm} é a distância por via.
 *
 * @param includesPickup {@code false} quando o estabelecimento não tem coordenada (RF-25.5): a rota
 *     ainda existe, mas vai direto ao destino. Dizer isso é o que impede o app de afirmar uma
 *     passagem pela loja que o trajeto não tem.
 * @param attribution RNF-25.2 — atribuição exigida pela licença do provedor, para o cliente exibir.
 */
public record OrderRouteResponse(
    UUID orderId,
    BigDecimal straightLineDistanceKm,
    Route route,
    String attribution,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  /**
   * {@code available = false} com {@code unavailableReason} é deliberado (RF-25.10): omitir a rota
   * deixaria o cliente adivinhando se o trajeto não existe ou se a chamada falhou — situações
   * diferentes, com telas diferentes.
   */
  public record Route(
      boolean available,
      String unavailableReason,
      boolean includesPickup,
      BigDecimal roadDistanceKm,
      Integer durationMinutes,
      List<Coordinate> geometry,
      List<Step> steps) {

    /** Sem posição do entregador não há de onde partir. */
    public static final String COURIER_LOCATION_UNKNOWN = "COURIER_LOCATION_UNKNOWN";

    /** Provedor fora do ar, cota estourada, tempo limite ou recurso desligado (RF-25.10). */
    public static final String ROUTING_UNAVAILABLE = "ROUTING_UNAVAILABLE";

    public static Route unavailable(String reason) {
      return new Route(false, reason, false, null, null, null, null);
    }
  }

  /**
   * Instrução de curva, em português, com o índice do ponto onde acontece — é o que permite ao app
   * saber qual passo está por vir sem recalcular geometria.
   */
  public record Step(String instruction, int distanceMeters, int pointIndex) {}

  public record Coordinate(BigDecimal lat, BigDecimal lng) {}
}
