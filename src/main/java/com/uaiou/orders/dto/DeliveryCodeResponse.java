package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.delivery.DeliveryCodeStatus;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** {@code GET /orders/{id}/delivery/code} — RF-15.11, só o estabelecimento dono. */
public record DeliveryCodeResponse(
    UUID orderId,
    String code,
    DeliveryCodeStatus status,
    Instant expiresAt,
    Audit audit,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  public record Audit(int readCount, Instant lastReadAt) {}
}
