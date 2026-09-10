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
    /** O {@code login} da conta — identidade estável, ao contrário do nome de exibição. */
    String username,
    String email,
    String telefone,
    MeProfile profile,
    DocumentsResponse documents,
    List<SanctionSummary> sanctions,
    /**
     * RF-12.7 — quantos estabelecimentos DISTINTOS bloquearam este entregador. Um bloqueio é
     * opinião; vários independentes são evidência para a moderação. Nulo para estabelecimento; na
     * v1 não entra no score (fora do escopo simplificado).
     */
    Long blockedByMerchantCount,
    Instant createdAt) {}
