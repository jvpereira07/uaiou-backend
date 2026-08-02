package com.uaiou.auth.web;

import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import java.util.UUID;

/**
 * O que o token carrega, decodificado. {@code status} é o valor do TOKEN, não necessariamente o
 * atual do banco — é cache (RF-03.9). Rotas de escrita revalidam contra o banco antes de confiar
 * nele.
 */
public record AuthenticatedUser(UUID userId, Role role, UserStatus status) {}
