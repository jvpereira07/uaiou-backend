package com.uaiou.tickets.dto;

import com.uaiou.tickets.TicketStatus;
import java.time.Instant;
import java.util.UUID;

/** Item de {@code GET /support-tickets} (RF-21.3). */
public record TicketSummary(
    UUID id,
    String subject,
    TicketStatus status,
    UUID authorId,
    String authorName,
    String referenceType,
    UUID referenceId,
    boolean contestedDelivery,
    Instant createdAt) {}
