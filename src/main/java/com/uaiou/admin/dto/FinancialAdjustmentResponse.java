package com.uaiou.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record FinancialAdjustmentResponse(
    String type, UUID targetUserId, int amount, String reason, Instant createdAt) {}
