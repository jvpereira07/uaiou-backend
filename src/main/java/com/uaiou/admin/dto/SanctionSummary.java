package com.uaiou.admin.dto;

import com.uaiou.users.SanctionType;
import java.time.Instant;
import java.util.UUID;

public record SanctionSummary(
    UUID id,
    UUID userId,
    SanctionType type,
    String reason,
    Instant start,
    Instant end,
    boolean active,
    UUID adminId,
    Instant createdAt) {}
