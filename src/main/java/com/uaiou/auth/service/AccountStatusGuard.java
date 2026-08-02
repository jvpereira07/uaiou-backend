package com.uaiou.auth.service;

import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.Sancao;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.SancaoRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Regra de bloqueio por status (RF-03.4), compartilhada entre a emissão de sessão ({@link
 * SessionService}) e a reconsulta feita pelo middleware em rotas de escrita ({@code
 * JwtAuthenticationFilter}, RF-03.9) — as duas precisam produzir exatamente o mesmo
 * 403/motivo/prazo para o mesmo estado de conta.
 */
@Component
public class AccountStatusGuard {

  private final SancaoRepository sancaoRepository;

  public AccountStatusGuard(SancaoRepository sancaoRepository) {
    this.sancaoRepository = sancaoRepository;
  }

  public void checkAllowsSession(Usuario usuario) {
    if (usuario.getStatus() == UserStatus.BANNED) {
      String reason = blockingReason(usuario.getId());
      throw new ForbiddenException("ACCOUNT_BANNED", reason, Map.of("reason", reason));
    }
    if (usuario.getStatus() == UserStatus.SUSPENDED) {
      Optional<Sancao> sancao = sancaoRepository.findBlockingSanction(usuario.getId());
      String reason = sancao.map(Sancao::getMotivo).orElse("Conta suspensa.");
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("reason", reason);
      sancao.map(Sancao::getFim).ifPresent(until -> details.put("until", until.toString()));
      throw new ForbiddenException("ACCOUNT_SUSPENDED", reason, details);
    }
  }

  private String blockingReason(UUID usuarioId) {
    return sancaoRepository
        .findBlockingSanction(usuarioId)
        .map(Sancao::getMotivo)
        .orElse("Conta banida.");
  }
}
