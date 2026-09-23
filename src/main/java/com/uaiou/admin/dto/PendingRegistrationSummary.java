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
    List<DocumentReviewItem> documents,
    /**
     * Exigidos pelo papel e ainda não enviados. Vazio = pronto para aprovar; não-vazio = o admin vê
     * o cadastro, mas só pode rejeitar ou esperar.
     */
    List<Purpose> missingDocuments) {

  public record DocumentReviewItem(
      UUID documentId, Purpose type, DocumentApprovalStatus status, String fileUrl) {}
}
