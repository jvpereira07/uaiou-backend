package com.uaiou.admin.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import org.springframework.stereotype.Component;

/**
 * T-07 é o primeiro módulo com rotas restritas por papel — não existe {@code @PreAuthorize} nem
 * Spring Security neste projeto (autorização é sempre checagem manual + exceção tipada, mesmo
 * estilo de {@link com.uaiou.auth.service.AccountStatusGuard}). Níveis de admin (atendente/pleno/
 * superadmin) são {@code TODO(dono)} — hoje todo admin autenticado tem todos os poderes.
 */
@Component
public class AdminAccessGuard {

  public void require(AuthenticatedUser user) {
    if (user.role() != Role.ADMIN) {
      throw new ForbiddenException("ADMIN_ONLY", "Rota restrita a administradores.");
    }
  }
}
