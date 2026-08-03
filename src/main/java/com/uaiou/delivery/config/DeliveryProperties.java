package com.uaiou.delivery.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code cipherKey} é a chave separada do banco (RN-08.3): o código legível vive cifrado, e
 * comprometer o banco sozinho não entrega os códigos ativos. Vem de {@code
 * DELIVERY_CODE_CIPHER_KEY}, já previsto no docker-compose desde T-02.
 *
 * <p>{@code codeTtl} — RN-08.7 (expiração do código por tempo). A política fina de expiração é de
 * T-15; T-13 só precisa nascer com um prazo, e ele é configuração desde já para não virar literal.
 */
@ConfigurationProperties(prefix = "app.delivery")
public record DeliveryProperties(String cipherKey, Duration codeTtl) {}
