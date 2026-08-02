package com.uaiou.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * {@code PUT /admin/registrations/{userId}/review} (api/admin.md). {@code documentIds} só é usado
 * (e é obrigatório) quando {@code decision = rejected} — aprovação vale para todos os documentos
 * pendentes do cadastro de uma vez, não tem o que citar.
 */
public record ReviewRegistrationRequest(
    @NotNull ReviewDecision decision, @NotBlank String reason, List<UUID> documentIds) {}
