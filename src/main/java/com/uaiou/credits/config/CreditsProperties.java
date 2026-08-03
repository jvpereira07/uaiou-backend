package com.uaiou.credits.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** RF-09.7 — custo por postagem é configuração, não literal no código (é {@code TODO(dono)}). */
@ConfigurationProperties(prefix = "app.credits")
public record CreditsProperties(int costPerOrder) {}
