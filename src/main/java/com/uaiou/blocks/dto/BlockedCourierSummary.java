package com.uaiou.blocks.dto;

import java.time.Instant;
import java.util.UUID;

/** RF-12.1 — "lista com nome, data e motivo". */
public record BlockedCourierSummary(
    UUID courierId, String courierName, String reason, Instant blockedAt) {}
