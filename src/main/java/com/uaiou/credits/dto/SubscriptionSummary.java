package com.uaiou.credits.dto;

import com.uaiou.credits.SubscriptionStatus;
import java.time.LocalDate;

public record SubscriptionSummary(
    String planName,
    int monthlyCredits,
    int consumedThisCycle,
    LocalDate renewsAt,
    SubscriptionStatus status) {}
