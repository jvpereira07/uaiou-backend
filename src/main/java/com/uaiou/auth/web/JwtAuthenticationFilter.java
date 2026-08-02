package com.uaiou.auth.web;

import com.uaiou.auth.service.AccountStatusGuard;
import com.uaiou.auth.service.JwtService;
import com.uaiou.shared.error.ApiException;
import com.uaiou.shared.error.UnauthorizedException;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * RF-03.9. Decodifica o Bearer token, se houver, e popula {@link CurrentUserHolder} — não rejeita
 * requisições sem token, porque nem toda rota exige autenticação; quem exige chama {@code
 * require()}.
 *
 * <p>Em métodos que não são "seguros" (tudo exceto GET/HEAD/OPTIONS — aproximação de "rota de
 * escrita", critério de aceite 10), reconsulta o {@code status} no banco em vez de confiar no valor
 * cacheado no token: um usuário banido não pode operar até o access token expirar. Rotas de leitura
 * sensíveis que venham a precisar da mesma reconsulta (nenhuma existe ainda em T-03) podem chamar
 * {@link AccountStatusGuard} diretamente.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

  private final JwtService jwtService;
  private final UsuarioRepository usuarioRepository;
  private final AccountStatusGuard accountStatusGuard;
  private final CurrentUserHolder currentUserHolder;
  private final HandlerExceptionResolver exceptionResolver;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      UsuarioRepository usuarioRepository,
      AccountStatusGuard accountStatusGuard,
      CurrentUserHolder currentUserHolder,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
    this.jwtService = jwtService;
    this.usuarioRepository = usuarioRepository;
    this.accountStatusGuard = accountStatusGuard;
    this.currentUserHolder = currentUserHolder;
    this.exceptionResolver = exceptionResolver;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      Optional<AuthenticatedUser> authenticatedUser =
          jwtService.parse(header.substring(BEARER_PREFIX.length()));
      if (authenticatedUser.isPresent()) {
        try {
          AuthenticatedUser user = authenticatedUser.get();
          if (!SAFE_METHODS.contains(request.getMethod())) {
            user = revalidateStatus(user);
          }
          currentUserHolder.set(user);
        } catch (ApiException e) {
          exceptionResolver.resolveException(request, response, null, e);
          return;
        }
      }
      // Token malformado/expirado: segue sem usuário autenticado — a rota decide se isso é um
      // problema.
    }
    chain.doFilter(request, response);
  }

  private AuthenticatedUser revalidateStatus(AuthenticatedUser cached) {
    Usuario usuario =
        usuarioRepository
            .findById(cached.userId())
            .orElseThrow(
                () -> new UnauthorizedException("INVALID_TOKEN", "Usuário não encontrado."));
    accountStatusGuard.checkAllowsSession(usuario);
    return new AuthenticatedUser(usuario.getId(), usuario.getTipo(), usuario.getStatus());
  }
}
