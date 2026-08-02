package com.uaiou.auth.dto;

import com.uaiou.users.Role;
import jakarta.validation.constraints.NotBlank;

/**
 * Um recurso, três credenciais (api/auth.md) — campos além do exigido por {@code grantType} ficam
 * nulos. Validados manualmente em {@code SessionService}, não por anotação: a obrigatoriedade
 * depende do {@code grantType} irmão, o que Bean Validation não expressa bem sem grupos de
 * validação — mais complexidade do que o ganho aqui.
 */
public record SessionRequest(
    @NotBlank String grantType,
    String login,
    String password,
    String idToken,
    String refreshToken,
    Role role) {}
