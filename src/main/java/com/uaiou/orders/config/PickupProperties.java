package com.uaiou.orders.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * T-26 — parâmetros de coleta, cancelamento e desistência. Todos provisórios por decisão do dono
 * (D4, D8): calibrar com dado de produção não pode exigir deploy.
 *
 * @param radiusMeters raio do estabelecimento dentro do qual o entregador "chegou" (RF-26.2).
 * @param requiredReadings leituras consecutivas no raio para registrar a chegada (RF-26.2).
 * @param reminderAfter espera, desde a chegada, até o entregador poder pedir novo aviso (RF-26.10).
 * @param reminderMinInterval intervalo mínimo entre dois avisos pedidos pelo entregador (RF-26.10).
 * @param arrivedCancellationFeeRate fração do frete final devida ao entregador quando a loja cancela
 *     depois da chegada (RF-26.17/RF-26.18).
 * @param withdrawalMaxPer24h desistências que contam, em 24 h, até o bloqueio de aceite (RF-26.29).
 * @param withdrawalCooldown duração do bloqueio de aceite (RF-26.29).
 * @param withdrawalPickupDelayTolerance espera na loja a partir da qual desistir por atraso não
 *     penaliza (RF-26.30).
 */
@ConfigurationProperties(prefix = "app.pickup")
public record PickupProperties(
    double radiusMeters,
    int requiredReadings,
    Duration reminderAfter,
    Duration reminderMinInterval,
    BigDecimal arrivedCancellationFeeRate,
    int withdrawalMaxPer24h,
    Duration withdrawalCooldown,
    Duration withdrawalPickupDelayTolerance) {}
