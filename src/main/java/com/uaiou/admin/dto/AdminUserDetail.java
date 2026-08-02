package com.uaiou.admin.dto;

import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import com.uaiou.users.dto.DocumentsResponse;
import com.uaiou.users.dto.MeProfile;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** RF-07.8 — {@code GET /admin/users/{id}}: perfil completo + histórico. */
public record AdminUserDetail(
    UUID id,
    Role role,
    UserStatus status,
    String displayName,
    String email,
    String telefone,
    MeProfile profile,
    DocumentsResponse documents,
    List<SanctionSummary> sanctions,
    Instant createdAt) {}
