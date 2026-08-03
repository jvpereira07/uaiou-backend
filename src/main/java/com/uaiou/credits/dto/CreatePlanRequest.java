package com.uaiou.credits.dto;

import com.uaiou.shared.money.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code price} não é validada por bean validation (não é um {@code Number}) — checada no serviço.
 */
public record CreatePlanRequest(
    @NotBlank String name, @Positive Integer monthlyCredits, @NotNull Money price) {}
