package com.uaiou.users.dto;

import java.math.BigDecimal;

/**
 * Endereço do estabelecimento — campo livre (RF-04.3, T-04), sempre trocado como um grupo.
 *
 * <p>{@code lat}/{@code lng} são opcionais (nulos quando o estabelecimento nunca marcou a
 * localização no mapa) — diferente do destino do pedido, este endereço nunca é geocodificado
 * automaticamente pelo servidor.
 */
public record Address(
    String bairro,
    String rua,
    String numero,
    String cidade,
    String cep,
    BigDecimal lat,
    BigDecimal lng) {}
