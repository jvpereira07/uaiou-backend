package com.uaiou.credits.dto;

import com.uaiou.shared.money.Money;
import java.time.Instant;
import java.util.UUID;

public record PlanSummary(
    UUID id, String name, int monthlyCredits, Money price, boolean active, Instant createdAt) {}
