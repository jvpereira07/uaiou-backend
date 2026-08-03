package com.uaiou.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RF-11.11 — o raio de elegibilidade é configuração, não literal. A task registra "raio único na
 * v1; ajuste por região é fase 2", então o valor certo ainda vai ser calibrado contra densidade
 * real de entregadores.
 */
@ConfigurationProperties(prefix = "app.orders")
public record OrdersProperties(double eligibilityRadiusKm) {}
