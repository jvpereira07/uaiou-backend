package com.uaiou.admin.dto;

import com.uaiou.users.SanctionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * {@code POST /admin/users/{id}/sanctions} (api/admin.md). {@code expiresAt} é obrigatório para
 * {@code suspension} e proibido para {@code ban} — mesma regra do banco (ck_sancao_fim_coerente).
 */
public record CreateSanctionRequest(
    @NotNull SanctionType type, @NotBlank String reason, Instant expiresAt) {}
