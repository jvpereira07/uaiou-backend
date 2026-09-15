package com.uaiou.delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.delivery.LedgerEntryType;
import com.uaiou.delivery.LedgerStatus;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.shared.pagination.PageMeta;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code GET /me/earnings} (RF-18.3) — total do período + extrato paginado. */
public record EarningsResponse(
    Summary summary,
    List<Entry> data,
    PageMeta meta,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  public record Summary(Money total, Money receivable, Money settled) {}

  public record Entry(
      UUID id,
      UUID orderId,
      String orderNumber,
      Money amount,
      LedgerStatus status,
      Instant createdAt,
      Instant settledAt,
      /** T-26 — frete ou taxa de cancelamento (RF-A15.13). */
      LedgerEntryType type) {}
}
