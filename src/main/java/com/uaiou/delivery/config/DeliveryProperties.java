package com.uaiou.delivery.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RF-15.13/RF-16.9 — parâmetros que a documentação pede explicitamente como configuração, nunca
 * literal no código.
 *
 * @param cipherKey chave da cifra do código, separada do banco (RN-08.3).
 * @param codeTtl validade do código (RN-08.7).
 * @param geofenceRadiusMeters raio dentro do qual a finalização é permitida (RN-08.1).
 * @param maxAttempts tentativas de código antes do bloqueio (RF-15.8).
 * @param positionDivergenceToleranceMeters divergência entre o corpo da requisição e a última
 *     posição reportada que ainda não marca a entrega para revisão (RF-15.7).
 * @param contingencyDeadline prazo do degrau 2 até a atribuição de falha (RF-16.3/RF-16.5).
 * @param smsResendLimit reenvios do degrau 1 antes de escalar ao degrau 2 (RF-16.2).
 * @param penaltyPoints pontos da penalidade objetiva ao estabelecimento (RF-16.5).
 * @param contestableWindow janela de contestação até a consolidação automática (RF-17.6).
 */
@ConfigurationProperties(prefix = "app.delivery")
public record DeliveryProperties(
    String cipherKey,
    Duration codeTtl,
    double geofenceRadiusMeters,
    int maxAttempts,
    double positionDivergenceToleranceMeters,
    Duration contingencyDeadline,
    int smsResendLimit,
    int penaltyPoints,
    Duration contestableWindow) {}
