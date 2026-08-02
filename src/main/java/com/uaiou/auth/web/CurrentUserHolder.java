package com.uaiou.auth.web;

import com.uaiou.shared.error.UnauthorizedException;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Escopo de requisição (RF-03.9): {@link JwtAuthenticationFilter} popula, controllers leem. Ausente
 * sempre que não há token, o token é inválido/expirado, ou (em rota de escrita) a reconsulta ao
 * banco barrou o usuário — nesses casos {@link #require()} é quem produz o 401 uniforme.
 */
@Component
@RequestScope
public class CurrentUserHolder {

  private AuthenticatedUser user;

  public void set(AuthenticatedUser user) {
    this.user = user;
  }

  public Optional<AuthenticatedUser> get() {
    return Optional.ofNullable(user);
  }

  public AuthenticatedUser require() {
    return get()
        .orElseThrow(() -> new UnauthorizedException("MISSING_TOKEN", "Autenticação necessária."));
  }
}
