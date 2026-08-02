package com.uaiou.auth.service;

import com.uaiou.auth.config.PasswordResetProperties;
import com.uaiou.auth.entity.PasswordResetToken;
import com.uaiou.auth.repository.PasswordResetTokenRepository;
import com.uaiou.auth.repository.RefreshTokenRepository;
import com.uaiou.shared.error.UnauthorizedException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-03.11. */
@Service
public class PasswordResetService {

  private static final String RESET_TOKEN_PREFIX = "pr_";

  private final UsuarioRepository usuarioRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final EmailSender emailSender;
  private final PasswordResetProperties properties;

  public PasswordResetService(
      UsuarioRepository usuarioRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      EmailSender emailSender,
      PasswordResetProperties properties) {
    this.usuarioRepository = usuarioRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.emailSender = emailSender;
    this.properties = properties;
  }

  /**
   * Sempre "conclui com sucesso" do ponto de vista do chamador (o controller sempre responde 202) —
   * se o e-mail não existir, este método simplesmente não faz nada, sem diferença observável de
   * fora (critério de aceite 9: não virar oráculo de enumeração de contas).
   */
  @Transactional
  public void requestReset(String email) {
    usuarioRepository
        .findByEmail(email)
        .ifPresent(
            usuario -> {
              String rawToken = OpaqueTokens.generate(RESET_TOKEN_PREFIX);
              Instant expiraEm = Instant.now().plus(properties.ttlMinutes(), ChronoUnit.MINUTES);
              passwordResetTokenRepository.save(
                  new PasswordResetToken(
                      UuidV7.next(), usuario.getId(), OpaqueTokens.hash(rawToken), expiraEm));

              String link = properties.baseUrl() + "/reset-password?token=" + rawToken;
              emailSender.sendPasswordReset(usuario.getEmail(), link);
            });
  }

  @Transactional
  public void confirmReset(String rawToken, String newPassword) {
    String hash = OpaqueTokens.hash(rawToken);
    PasswordResetToken token =
        passwordResetTokenRepository
            .findByTokenHash(hash)
            .filter(PasswordResetToken::isValido)
            .orElseThrow(this::invalidTokenException);

    Usuario usuario =
        usuarioRepository.findById(token.getUsuarioId()).orElseThrow(this::invalidTokenException);

    usuario.alterarSenha(passwordEncoder.encode(newPassword));
    usuarioRepository.save(usuario);

    token.marcarUsado();
    passwordResetTokenRepository.save(token);

    // Troca de senha revoga todas as sessões — o caso de uso típico é conta comprometida (critério
    // 8).
    refreshTokenRepository.revogarTodosDoUsuario(usuario.getId(), Instant.now());
  }

  private UnauthorizedException invalidTokenException() {
    return new UnauthorizedException(
        "INVALID_RESET_TOKEN", "Token de redefinição inválido, expirado ou já usado.");
  }
}
