package com.uaiou.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** {@code POST /support-tickets} (RF-21.1). {@code reference} é opcional. */
public record CreateTicketRequest(
    @NotBlank String subject, @NotBlank String message, ReferenceRef reference) {

  public record ReferenceRef(String type, UUID id) {}
}
