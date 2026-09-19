package com.uaiou.admin.dto;

import com.uaiou.orders.limits.BehaviorLimitRule;
import java.time.Instant;

/** Um limite de comportamento como o painel o mostra (V27). */
public record BehaviorLimitResponse(
    BehaviorLimitRule rule,
    int max,
    int windowMinutes,
    int blockMinutes,
    boolean active,
    Instant updatedAt,
    AdminOrderDetail.Ref updatedBy) {}
