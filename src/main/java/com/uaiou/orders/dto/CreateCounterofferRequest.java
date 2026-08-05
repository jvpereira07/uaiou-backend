package com.uaiou.orders.dto;

import com.uaiou.shared.money.Money;
import jakarta.validation.constraints.NotNull;

/** {@code POST /orders/{id}/counteroffers} (RF-14.1). */
public record CreateCounterofferRequest(@NotNull Money proposedFee) {}
