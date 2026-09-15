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
 *
 * <p>T-26: {@code pendingCancellationFee} é a taxa que o estabelecimento pagaria cancelando
 * <em>agora</em> — vem do servidor para o cliente exibir antes da confirmação (RF-A15.5), sem
 * recalcular percentual. {@code pickupLocationKnown} diz se a loja marcou a coordenada; sem ela não
 * há aviso de chegada (RF-26.5).
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
    /** Nome e logo da loja — o entregador vê para onde vai na retirada. */
    OrderSummary.MerchantRef merchant,
    Instant acceptedAt,
    Instant arrivedAt,
    Instant pickedUpAt,
    CancellationRef cancellation,
    Money pendingCancellationFee,
    boolean pickupLocationKnown,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  /** RF-26.6 — foto e placa identificam o entregador na porta da loja. */
  public record CourierRef(UUID id, String name, String vehiclePlate, String photoUrl) {}

  public record ReceiverResponse(String name, String phone) {}

  public record CancellationRef(String reason, String note, Instant cancelledAt) {}
}
