package com.uaiou.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.uaiou.users.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** RF-07.8 — uma linha de {@code GET /admin/merchants}. */
public record MerchantSummary(
    UUID id,
    String displayName,
    String email,
    UserStatus status,
    String cnpj,
    String businessName,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal score,
    Instant createdAt) {}
