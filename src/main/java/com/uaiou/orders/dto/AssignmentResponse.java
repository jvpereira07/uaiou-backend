package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * {@code POST /orders/{id}/assignment} (api/pedidos.md).
 *
 * <p>O <strong>código de entrega não aparece aqui</strong> (RN-08.3): ele é gerado nesta transação,
 * mas trafega só pelos canais de envio e pela rota de leitura auditada (T-15). Devolvê-lo na
 * resposta do aceite anularia a auditoria antes mesmo de ela existir.
 */
public record AssignmentResponse(
    UUID orderId,
    UUID courierId,
    Money finalFee,
    Instant assignedAt,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
