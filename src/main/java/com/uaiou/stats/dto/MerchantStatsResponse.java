package com.uaiou.stats.dto;

import com.uaiou.shared.money.Money;
import java.math.BigDecimal;

/**
 * {@code GET /me/stats} do estabelecimento (RF-22.3). {@code codeContingencyRate} é obrigatória
 * (RF-22.4): é o indicador que precede a penalidade, e a RN-09.3/09.4 exige que o estabelecimento o
 * veja antes de ser cobrado — nunca omitido, mesmo quando zero.
 */
public record MerchantStatsResponse(
    StatsPeriod period,
    int ordersPublished,
    int ordersCompleted,
    BigDecimal matchRate,
    Double medianTimeToAssignmentMinutes,
    BigDecimal counterofferAcceptRate,
    Money spread,
    Money freightSpend,
    int creditsConsumed,
    Integer creditsQuota,
    BigDecimal codeContingencyRate) {}
