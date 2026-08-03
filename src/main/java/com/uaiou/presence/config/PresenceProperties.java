package com.uaiou.presence.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RF-10.7 — o limite de frescor da posição é parâmetro, não literal: o valor certo depende da
 * densidade de entregadores e do intervalo de report do cliente (ainda indefinido, ver o aviso de
 * T-10), então vai mudar.
 *
 * <p>{@link Duration} e não "minutos inteiros": o mesmo limite serve de TTL no Redis, e amarrar a
 * configuração à granularidade de minuto tornaria o comportamento de expiração inverificável em
 * teste sem esperar minutos de verdade.
 */
@ConfigurationProperties(prefix = "app.presence")
public record PresenceProperties(Duration freshness) {}
