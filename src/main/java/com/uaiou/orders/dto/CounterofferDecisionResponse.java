package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import java.util.Map;
import java.util.UUID;

/** {@code PUT /counteroffers/{id}/decision} — 200 (api/pedidos.md). */
public record CounterofferDecisionResponse(
    UUID id,
    CounterofferStatus status,
    Money proposedFee,
    OrderRef order,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  public record OrderRef(UUID id, OrderStatus status, Money finalFee) {}
}
