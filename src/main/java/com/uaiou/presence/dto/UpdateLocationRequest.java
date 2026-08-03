package com.uaiou.presence.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * {@code PUT /me/location} (api/usuarios.md). O JSON usa {@code lng}; a coluna no banco é {@code
 * long} — os dois nomes existem porque nenhum dos dois serve para os dois lados ({@code long} é
 * palavra reservada em Java, {@code lng} não é o nome do modelo de domínio).
 *
 * <p>{@code accuracy} em metros, opcional: nem todo dispositivo reporta. Ausente significa
 * "desconhecida", e quem consome (T-15, RF-10.9) decide se isso basta para habilitar finalização.
 */
public record UpdateLocationRequest(
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal lat,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal lng,
    @PositiveOrZero BigDecimal accuracy) {}
