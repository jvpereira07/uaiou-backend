package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta de {@code POST /orders} e {@code GET /orders/{id}} (api/pedidos.md).
 *
 * <p>RF-11.9: o <strong>código de entrega nunca aparece aqui</strong> (RN-08.3) — nem para as
 * partes. Ele tem rota própria, com leitura auditada, em T-15.
 */
public record OrderResponse(
    UUID id,
    String number,
    OrderStatus status,
    Money proposedFee,
    Money finalFee,
    int creditsConsumed,
    Instant createdAt,
    Instant expectedDeliveryAt,
    DestinationResponse destination,
    ReceiverResponse receiver,
    CourierRef courier,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  public record CourierRef(UUID id, String name) {}

  public record ReceiverResponse(String name, String phone) {}
}
