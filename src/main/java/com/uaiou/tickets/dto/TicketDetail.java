package com.uaiou.tickets.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.tickets.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /support-tickets/{id}} (RF-21.4) — thread completo. {@code supervisionLinks} só é
 * preenchido para o admin (T-07): o próprio autor não precisa de um atalho para a supervisão de si
 * mesmo.
 */
public record TicketDetail(
    UUID id,
    String subject,
    TicketStatus status,
    UUID authorId,
    String authorName,
    UUID adminId,
    String referenceType,
    UUID referenceId,
    String adjustmentType,
    UUID adjustmentId,
    boolean contestedDelivery,
    Instant resolvedAt,
    Instant createdAt,
    List<Message> messages,
    @JsonProperty("_links") Map<String, LinkRef> supervisionLinks) {

  public record Message(
      UUID id, UUID authorId, String authorName, boolean isAdmin, String text, Instant createdAt) {}
}
