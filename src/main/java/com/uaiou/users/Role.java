package com.uaiou.users;

/**
 * Vocabulário do papel do usuário, em inglês — o que trafega em JSON e no JWT (api/auth.md). A
 * coluna {@code usuario.tipo} no banco é em português; a tradução acontece em {@link
 * RoleConverter}, num único lugar, para o resto do código nunca precisar pensar nas duas
 * representações ao mesmo tempo.
 */
public enum Role {
  MERCHANT,
  COURIER,
  ADMIN
}
