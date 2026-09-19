package com.uaiou.admin.dto;

import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.money.Money;
import java.time.Instant;
import java.util.UUID;

/** Uma linha de {@code GET /admin/orders} — histórico de entregas do sistema inteiro. */
public record AdminOrderSummary(
    UUID id,
    String number,
    OrderStatus status,
    AdminOrderDetail.Ref merchant,
    AdminOrderDetail.Ref courier,
    Money proposedFee,
    Money finalFee,
    String neighborhood,
    Instant createdAt,
    Instant acceptedAt,
    Instant pickedUpAt,
    Instant finalizedAt,
    Instant cancelledAt,
    String cancellationReason) {}
