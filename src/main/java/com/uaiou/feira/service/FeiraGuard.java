package com.uaiou.feira.service;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.feira.config.FeiraProperties;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.users.Role;
import org.springframework.stereotype.Component;

/**
 * Porta de entrada do modo feira. Com a flag desligada as rotas respondem 404, não 403: um sistema
 * em produção não deve nem admitir que este modo existe.
 */
@Component
public class FeiraGuard {

  private final FeiraProperties properties;

  public FeiraGuard(FeiraProperties properties) {
    this.properties = properties;
  }

  public void requireEnabled() {
    if (!properties.enabled()) {
      throw new NotFoundException("FEIRA_DISABLED", "Modo Fetin não está habilitado.");
    }
  }

  public void requireAdmin(AuthenticatedUser user) {
    requireEnabled();
    if (user.role() != Role.ADMIN) {
      throw new ForbiddenException("ADMIN_ONLY", "Rota restrita a administradores.");
    }
  }

  public void requireJogador(AuthenticatedUser user) {
    requireEnabled();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException("COURIER_ONLY", "Rota restrita a jogadores.");
    }
  }
}
