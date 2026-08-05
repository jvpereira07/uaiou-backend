package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.Map;

/** {@code POST /orders/{id}/delivery/code-recoveries} — 201 (RF-16.2/RF-16.3/RF-16.7). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CodeRecoveryResponse(
    int step,
    boolean contestableReleased,
    Instant merchantDeadlineAt,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
