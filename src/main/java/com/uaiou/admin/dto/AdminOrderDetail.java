package com.uaiou.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /admin/orders/{id}} (RF-21.8) — espelho administrativo do pedido, com a linha do tempo
 * composta por união de {@code pedido}, {@code contraoferta}, {@code contingencia_otp}, {@code
 * evidencia_entrega}, {@code lancamento_frete}, {@code desistencia_pedido}, {@code
 * ocorrencia_timeout} e {@code registro_auditoria}. {@code deliveryCode} nunca aparece
 * (RF-21.9/RF-15.11) — nem para o admin.
 */
public record AdminOrderDetail(
    UUID id,
    String number,
    OrderStatus status,
    Ref merchant,
    Ref courier,
    Money proposedFee,
    Money finalFee,
    boolean contestedDelivery,
    Instant createdAt,
    Cancellation cancellation,
    List<AdminOrderAction> availableActions,
    List<TimelineEvent> timeline,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  public record Ref(UUID id, String name) {}

  public record Cancellation(String reason, String note, Instant cancelledAt) {}

  public record TimelineEvent(Instant at, String event, Map<String, Object> details) {}
}
