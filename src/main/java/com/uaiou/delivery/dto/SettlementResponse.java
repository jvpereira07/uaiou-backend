package com.uaiou.delivery.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code POST /me/earnings/settlements} — 200. */
public record SettlementResponse(List<UUID> settled, Instant settledAt) {}
