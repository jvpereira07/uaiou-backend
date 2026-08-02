package com.uaiou.users.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resposta de {@code GET /me} e de {@code PATCH /me} (RF-04.1/RF-04.4) — o mesmo formato serve às
 * duas rotas; {@code pendingFields} é {@code null} (e some do JSON) em {@code GET}, e uma lista
 * (possivelmente vazia) em {@code PATCH}, para o app distinguir "nunca perguntei" de "perguntei,
 * nada ficou pendente".
 */
public record MeResponse(
    UUID id,
    Role role,
    UserStatus status,
    String displayName,
    String email,
    String telefone,
    MeProfile profile,
    List<String> pendingFields,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
