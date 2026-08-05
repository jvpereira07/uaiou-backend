package com.uaiou.reviews.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /orders/{id}/reviews} (RF-19.2/RF-19.4). Sem {@code targetId}: o alvo é sempre
 * derivado do pedido, nunca enviado pelo cliente (RF-19.3) — aceitar um valor do corpo permitiria
 * avaliar terceiros.
 */
public record CreateReviewRequest(
    @NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {}
