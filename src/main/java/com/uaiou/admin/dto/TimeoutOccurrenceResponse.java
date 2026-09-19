package com.uaiou.admin.dto;

import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.timeout.OrderTimeoutRule;
import java.time.Instant;
import java.util.UUID;

/** Uma linha do histórico de disparos de timeout. */
public record TimeoutOccurrenceResponse(
    UUID id,
    UUID orderId,
    String orderNumber,
    OrderTimeoutRule rule,
    OrderTimeoutRule.TimeoutAction action,
    OrderStatus previousStatus,
    Instant at) {}
