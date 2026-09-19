package com.uaiou.admin.dto;

import com.uaiou.orders.timeout.OrderTimeoutRule;
import java.time.Instant;

/**
 * Uma regra de timeout como o painel a mostra. {@code overdueNow} é quantos pedidos a regra
 * alcançaria agora com o prazo atual — serve para o admin medir o efeito antes de ligar.
 */
public record TimeoutSettingResponse(
    OrderTimeoutRule rule,
    OrderTimeoutRule.TimeoutAction action,
    int minutes,
    boolean active,
    int overdueNow,
    Instant updatedAt,
    AdminOrderDetail.Ref updatedBy) {}
