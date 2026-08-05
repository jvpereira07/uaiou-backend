package com.uaiou.stats.dto;

import com.uaiou.shared.money.Money;
import java.math.BigDecimal;

/**
 * {@code GET /me/stats} do entregador (RF-22.2). {@code availableHours}/{@code utilization} vêm
 * nulos: T-10 guarda só o estado ATUAL de disponibilidade ({@code disponivel}/{@code
 * disponivelDesde}), não um log histórico de alternâncias — não existe insumo para agregar isso por
 * período sem inventar uma fonte de dados que não foi especificada em nenhuma task anterior.
 */
public record CourierStatsResponse(
    StatsPeriod period,
    int deliveriesCompleted,
    Money earningsReceivable,
    Money earningsSettled,
    Money averageTicket,
    BigDecimal counterofferSuccessRate,
    Double averageDeliveryMinutes,
    BigDecimal cleanFinalizationRate,
    Double availableHours,
    BigDecimal utilization) {}
