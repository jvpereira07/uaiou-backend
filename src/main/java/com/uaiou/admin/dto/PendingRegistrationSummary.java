package com.uaiou.admin.dto;

import com.uaiou.uploads.Purpose;
import com.uaiou.users.DocumentApprovalStatus;
import com.uaiou.users.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF-07.3 — uma linha da fila de {@code GET /admin/registrations?status=pending}. */
public record PendingRegistrationSummary(
    UUID userId,
    Role role,
    String displayName,
    String email,
    Instant registeredAt,
    List<DocumentReviewItem> documents) {

  public record DocumentReviewItem(
      UUID documentId, Purpose type, DocumentApprovalStatus status, String fileUrl) {}
}
