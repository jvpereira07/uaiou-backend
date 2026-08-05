package com.uaiou.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** {@code PUT /support-tickets/{id}/resolution} (RF-21.6). {@code adjustmentId} é opcional. */
public record ResolveTicketRequest(
    @NotBlank String finalResponse, String adjustmentType, UUID adjustmentId) {}
