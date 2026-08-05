package com.uaiou.tickets.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /support-tickets/{id}/messages} (RF-21.5). */
public record AddMessageRequest(@NotBlank String message) {}
