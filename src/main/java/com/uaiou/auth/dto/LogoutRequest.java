package com.uaiou.auth.dto;

/**
 * Corpo opcional de {@code DELETE /auth/sessions/current}: identifica qual refresh token (aparelho)
 * sair — o access token sozinho não carrega {@code familiaId}, então sem isso não haveria como
 * saber qual sessão entre vários aparelhos logados é "a atual". Ausente ou não reconhecido → 204
 * mesmo assim (logout é idempotente, RF-03.10).
 */
public record LogoutRequest(String refreshToken) {}
