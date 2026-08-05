package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** {@code POST /orders/{id}/delivery/code-dispatches} — 201 (RF-16.4). */
public record CodeDispatchResponse(
    UUID orderId, Instant dispatchedAt, @JsonProperty("_links") Map<String, LinkRef> links) {}
