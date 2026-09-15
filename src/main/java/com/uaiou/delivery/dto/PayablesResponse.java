package com.uaiou.delivery.dto;

import com.uaiou.delivery.LedgerEntryType;
import com.uaiou.delivery.LedgerStatus;
import com.uaiou.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code GET /me/payables} (RF-18.6) — espelho do estabelecimento, agrupado por entregador. */
public record PayablesResponse(Money total, List<ByCourier> byCourier) {

  public record ByCourier(UUID courierId, String courierName, Money total, List<Entry> entries) {}

  public record Entry(
      UUID id,
      UUID orderId,
      String orderNumber,
      Money amount,
      LedgerStatus status,
      Instant createdAt,
      /** T-26 — frete ou taxa de cancelamento (RF-A15.13). */
      LedgerEntryType type) {}
}
