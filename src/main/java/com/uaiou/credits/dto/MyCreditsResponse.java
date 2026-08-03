package com.uaiou.credits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import java.util.Map;

/** RF-09.8 — {@code GET /me/credits} (api/financeiro.md). */
public record MyCreditsResponse(
    int creditsBalance,
    SubscriptionSummary subscription,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
