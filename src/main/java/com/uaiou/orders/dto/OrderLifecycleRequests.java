package com.uaiou.orders.dto;

import com.uaiou.orders.CancellationReason;
import com.uaiou.orders.WithdrawalReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Corpos das rotas de T-26. */
public final class OrderLifecycleRequests {

  private OrderLifecycleRequests() {}

  /** RF-26.14/RF-26.16 — {@code note} obrigatória quando {@code reason = other}. */
  public record CancellationRequest(
      @NotNull CancellationReason reason, @Size(max = 280) String note) {}

  /** RF-26.22/RF-26.25 — {@code note} obrigatória quando {@code reason = other}. */
  public record WithdrawalRequest(@NotNull WithdrawalReason reason, @Size(max = 280) String note) {}

  /** RF-26.3 — posição atual do aparelho no momento do "Cheguei". */
  public record PickupArrivalRequest(@NotNull BigDecimal lat, @NotNull BigDecimal lng) {}
}
