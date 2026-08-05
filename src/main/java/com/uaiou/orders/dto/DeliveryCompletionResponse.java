package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.delivery.FinalizationType;
import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code POST /orders/{id}/delivery/completion} — 201.
 *
 * <p>Sem {@code feeSettlement} (api/entregas.md mostra {@code reserved}/{@code available}): a v1
 * não custodia dinheiro (escopo-v1.md, RN-01.3/RN-10.3 alteradas) — o que existe é o lançamento em
 * {@code lancamento_frete}, sempre {@code a_receber}, sem reserva a distinguir os dois modos.
 */
public record DeliveryCompletionResponse(
    UUID orderId,
    OrderStatus orderStatus,
    FinalizationType completionType,
    Instant completedAt,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
