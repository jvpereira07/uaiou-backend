package com.uaiou.reviews.dto;

import java.time.Instant;
import java.util.UUID;

/** {@code POST /orders/{id}/reviews} — 201. */
public record ReviewResponse(
    UUID id, UUID orderId, UUID targetId, int rating, String comment, Instant createdAt) {}
