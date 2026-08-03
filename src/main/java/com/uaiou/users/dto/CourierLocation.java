package com.uaiou.users.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Última posição conhecida do entregador em {@code GET /me} (api/usuarios.md). Só o próprio
 * entregador e o admin veem isto — expor a posição ao estabelecimento está fora de escopo (T-10).
 * {@code accuracy} não vai no contrato de leitura: é insumo interno de T-15, não informação de
 * tela.
 */
public record CourierLocation(BigDecimal lat, BigDecimal lng, Instant updatedAt) {}
