package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta das rotas de T-26 (chegada, coleta, reaviso, cancelamento, desistência): o estado do
 * pedido depois da transição. {@code cancellationFee} só vem preenchido no cancelamento que gerou
 * taxa (RF-26.17).
 */
public record OrderLifecycleResponse(
    UUID orderId,
    OrderStatus status,
    Instant arrivedAt,
    Instant pickedUpAt,
    Instant cancelledAt,
    Money cancellationFee,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
