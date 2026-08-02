package com.uaiou.admin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** RF-07.9 — uma linha de {@code GET /admin/audit-logs} (api/admin.md). */
public record AuditLogEntry(
    UUID id,
    String action,
    AdminRef admin,
    @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
    String reason,
    ReferenceRef reference,
    Instant createdAt) {

  public record AdminRef(UUID id, String name) {}

  public record ReferenceRef(String type, UUID id) {}
}
