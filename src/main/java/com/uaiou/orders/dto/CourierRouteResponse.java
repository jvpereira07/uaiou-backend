package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.orders.dto.OrderRouteResponse.Coordinate;
import com.uaiou.orders.dto.OrderRouteResponse.Step;
import com.uaiou.shared.pagination.LinkRef;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta de {@code GET /couriers/me/route} — <strong>um trajeto só</strong> cobrindo todos os
 * pedidos em andamento do entregador, na ordem em que as paradas devem ser feitas.
 *
 * @param available mesmo contrato de {@link OrderRouteResponse.Route#available()} (RF-25.10): sem
 *     trajeto, {@code stops} ainda vem preenchido na ordem sugerida — o entregador sabe aonde ir
 *     mesmo sem o traçado.
 * @param optimized {@code true} quando a ordem saiu da matriz de tempos por via; {@code false}
 *     quando o provedor não oferece matriz ou os pedidos passam do teto do sequenciador — aí a
 *     ordem é a de aceite, coletando antes de entregar.
 * @param stops paradas em ordem. {@code pointIndex} é o índice em {@code geometry} onde cada uma
 *     acontece; nulo sem trajeto ou quando o provedor não separou as pernas.
 */
public record CourierRouteResponse(
    boolean available,
    String unavailableReason,
    boolean optimized,
    BigDecimal roadDistanceKm,
    Integer durationMinutes,
    List<Coordinate> geometry,
    List<Step> steps,
    List<Stop> stops,
    String attribution,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  /** Nenhum pedido aceito ou coletado: não há o que rotear. */
  public static final String NO_ACTIVE_ORDERS = "NO_ACTIVE_ORDERS";

  public enum StopKind {
    PICKUP,
    DROPOFF
  }

  public record Stop(UUID orderId, StopKind kind, Coordinate location, Integer pointIndex) {}
}
