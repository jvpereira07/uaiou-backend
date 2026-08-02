package com.uaiou.auth.service;

import com.uaiou.auth.config.JwtProperties;
import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.auth.dto.SessionUser;
import com.uaiou.auth.entity.RefreshToken;
import com.uaiou.auth.repository.RefreshTokenRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.error.UnauthorizedException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.Role;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-03.3 a RF-03.8. Um recurso ({@code POST /auth/sessions}), três credenciais via {@code
 * grantType}.
 */
@Service
public class SessionService {

  private static final String REFRESH_TOKEN_PREFIX = "rt_";

  private final UsuarioRepository usuarioRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AccountStatusGuard accountStatusGuard;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final GoogleIdTokenVerifierPort googleIdTokenVerifier;
  private final long refreshTtlDays;

  /**
   * Hash de custo comparável a uma senha real — usado quando o login não existe, para não vazar por
   * tempo de resposta (critério análogo ao 9, aplicado aqui por padrão defensivo).
   */
  private final String dummyPasswordHash;

  public SessionService(
      UsuarioRepository usuarioRepository,
      RefreshTokenRepository refreshTokenRepository,
      AccountStatusGuard accountStatusGuard,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      GoogleIdTokenVerifierPort googleIdTokenVerifier,
      JwtProperties jwtProperties) {
    this.usuarioRepository = usuarioRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.accountStatusGuard = accountStatusGuard;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.googleIdTokenVerifier = googleIdTokenVerifier;
    this.refreshTtlDays = jwtProperties.refreshTtlDays();
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public SessionResponse createSession(SessionRequest request) {
    return switch (request.grantType()) {
      case "password" -> passwordGrant(request);
      case "google" -> googleGrant(request);
      case "refresh" -> refreshGrant(request);
      default ->
          throw new BadRequestException(
              "UNSUPPORTED_GRANT_TYPE",
              "grantType deve ser \"password\", \"google\" ou \"refresh\".");
    };
  }

  private SessionResponse passwordGrant(SessionRequest request) {
    requireField(request.login(), "login");
    requireField(request.password(), "password");
    requireRole(request.role());

    Optional<Usuario> found = usuarioRepository.findByLogin(request.login());
    // Roda o encode mesmo sem usuário, contra um hash fixo — login inexistente não pode responder
    // mais
    // rápido que senha errada (RF-03, mesmo raciocínio do critério 9 para password-reset).
    String hashToCompare = found.map(Usuario::getSenhaHash).orElse(dummyPasswordHash);
    boolean matches = passwordEncoder.matches(request.password(), hashToCompare);

    Usuario usuario =
        found
            .filter(u -> matches)
            .orElseThrow(
                () ->
                    new UnauthorizedException("INVALID_CREDENTIALS", "Login ou senha incorretos."));

    accountStatusGuard.checkAllowsSession(usuario);
    checkRoleMatches(usuario, request.role());
    return issueNewSession(usuario);
  }

  private SessionResponse googleGrant(SessionRequest request) {
    requireField(request.idToken(), "idToken");
    requireRole(request.role());

    GoogleIdentity identity =
        googleIdTokenVerifier
            .verify(request.idToken())
            .orElseThrow(
                () ->
                    new UnauthorizedException(
                        "INVALID_GOOGLE_TOKEN", "Token do Google inválido ou expirado."));

    Optional<Usuario> byGoogleId = usuarioRepository.findByGoogleId(identity.googleId());
    Usuario usuario;
    if (byGoogleId.isPresent()) {
      usuario = byGoogleId.get();
    } else if (identity.emailVerified()) {
      // Fallback por e-mail verificado (RF-03.6). Não cria conta implícita — só resolve uma já
      // existente, e
      // aproveita para vincular o google_id, evitando repetir este fallback nos próximos logins.
      usuario =
          usuarioRepository
              .findByEmail(identity.email())
              .orElseThrow(
                  () ->
                      new NotFoundException(
                          "GOOGLE_ACCOUNT_NOT_LINKED", "Nenhuma conta vinculada a este Google."));
      linkGoogleId(usuario, identity.googleId());
    } else {
      throw new NotFoundException(
          "GOOGLE_ACCOUNT_NOT_LINKED", "Nenhuma conta vinculada a este Google.");
    }

    accountStatusGuard.checkAllowsSession(usuario);
    checkRoleMatches(usuario, request.role());
    return issueNewSession(usuario);
  }

  private SessionResponse refreshGrant(SessionRequest request) {
    requireField(request.refreshToken(), "refreshToken");
    String hash = OpaqueTokens.hash(request.refreshToken());

    RefreshToken existing =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token inválido."));

    if (existing.getRevogadoEm() != null) {
      // Reuso de token já revogado é assinatura de vazamento (RF-03.8) — revoga a família inteira.
      refreshTokenRepository.revogarFamilia(existing.getFamiliaId(), Instant.now());
      throw new UnauthorizedException(
          "REFRESH_TOKEN_REUSED",
          "Este token já foi usado; todas as sessões da família foram encerradas.");
    }
    if (!existing.isValido()) {
      throw new UnauthorizedException("INVALID_REFRESH_TOKEN", "Refresh token expirado.");
    }

    Usuario usuario =
        usuarioRepository
            .findById(existing.getUsuarioId())
            .orElseThrow(
                () ->
                    new UnauthorizedException("INVALID_REFRESH_TOKEN", "Usuário não encontrado."));
    // Sancionado durante a vida do refresh também não pode renovar — senão a sanção é contornável
    // só
    // esperando o access token expirar.
    accountStatusGuard.checkAllowsSession(usuario);

    existing.revogar();
    refreshTokenRepository.save(existing);
    return issueSessionInFamily(usuario, existing.getFamiliaId());
  }

  /**
   * RF-03.10 — revoga só o refresh da sessão atual (não a família inteira: outros aparelhos
   * continuam logados). Silencioso se o token não existir, não pertencer ao usuário autenticado, ou
   * já ter sido revogado — logout é idempotente por natureza, não há erro útil a reportar em nenhum
   * desses casos.
   */
  @Transactional
  public void revokeCurrentSession(UUID usuarioId, String rawRefreshToken) {
    String hash = OpaqueTokens.hash(rawRefreshToken);
    refreshTokenRepository
        .findByTokenHash(hash)
        .filter(token -> token.getUsuarioId().equals(usuarioId))
        .filter(RefreshToken::isValido)
        .ifPresent(
            token -> {
              token.revogar();
              refreshTokenRepository.save(token);
            });
  }

  private void linkGoogleId(Usuario usuario, String googleId) {
    // Usuario não expõe setter de googleId de propósito (só este fluxo, muito específico, precisa
    // mudá-lo);
    // feito via query direta para não abrir mutabilidade geral na entidade por um caso só.
    usuarioRepository.linkGoogleId(usuario.getId(), googleId);
  }

  private void requireField(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException(
          "MISSING_FIELD", "\"" + fieldName + "\" é obrigatório para este grantType.");
    }
  }

  private void requireRole(Role role) {
    if (role == null) {
      throw new BadRequestException("MISSING_FIELD", "\"role\" é obrigatório para este grantType.");
    }
  }

  private void checkRoleMatches(Usuario usuario, Role expectedRole) {
    if (usuario.getTipo() != expectedRole) {
      // Toggle do protótipo (🖼 Estabelecimento | Entregador) — evita sessão do papel errado no app
      // errado.
      throw new BusinessRuleException(
          "ROLE_MISMATCH", "Esta conta não é do tipo informado.", "RF-03.5");
    }
  }

  private SessionResponse issueNewSession(Usuario usuario) {
    return issueSessionInFamily(usuario, UuidV7.next());
  }

  private SessionResponse issueSessionInFamily(Usuario usuario, UUID familiaId) {
    IssuedAccessToken access =
        jwtService.issueAccessToken(usuario.getId(), usuario.getTipo(), usuario.getStatus());

    String rawRefreshToken = OpaqueTokens.generate(REFRESH_TOKEN_PREFIX);
    Instant refreshExpiresAt = Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS);
    RefreshToken refreshToken =
        new RefreshToken(
            UuidV7.next(),
            usuario.getId(),
            OpaqueTokens.hash(rawRefreshToken),
            familiaId,
            refreshExpiresAt);
    refreshTokenRepository.save(refreshToken);

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("profile", LinkRef.get("/api/v1/me"));
    links.put("logout", new LinkRef("/api/v1/auth/sessions/current", "DELETE"));

    SessionUser sessionUser =
        new SessionUser(
            usuario.getId(), usuario.getTipo(), usuario.getStatus(), usuario.getNomeExibicao());
    return new SessionResponse(
        access.token(), rawRefreshToken, access.expiresInSeconds(), sessionUser, links);
  }
}
