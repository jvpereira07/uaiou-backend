package com.uaiou.delivery.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * {@code POST /me/earnings/settlements} (RF-18.4) — o entregador confirma um ou mais lançamentos.
 */
public record SettlementRequest(@NotEmpty List<UUID> lancamentoIds) {}
