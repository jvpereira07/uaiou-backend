package com.uaiou.reviews.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code GET /me/reviews?direction=pending} — entregas ainda por avaliar, dentro do prazo. */
public record PendingReviewEntry(
    UUID orderId,
    String orderNumber,
    UUID counterpartyId,
    String counterpartyName,
    Instant deadline) {}
