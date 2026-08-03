package com.uaiou.credits.dto;

import com.uaiou.shared.money.Money;
import jakarta.validation.constraints.Positive;

/**
 * Todo campo é opcional — PUT aplica só o que veio (mesma convenção de {@code PATCH /me}, T-04).
 */
public record UpdatePlanRequest(
    String name, @Positive Integer monthlyCredits, Money price, Boolean active) {}
