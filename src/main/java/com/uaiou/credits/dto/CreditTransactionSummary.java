package com.uaiou.credits.dto;

import com.uaiou.credits.CreditTransactionType;
import java.time.Instant;
import java.util.UUID;

/** RF-09.9 — uma linha de {@code GET /me/credits/transactions}. */
public record CreditTransactionSummary(
    UUID id, CreditTransactionType type, int quantity, UUID orderId, Instant createdAt) {}
