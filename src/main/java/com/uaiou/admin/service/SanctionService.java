package com.uaiou.admin.service;

import com.uaiou.admin.dto.CreateSanctionRequest;
import com.uaiou.admin.dto.SanctionSummary;
import com.uaiou.auth.repository.RefreshTokenRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.SanctionType;
import com.uaiou.users.entity.Sancao;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.SancaoRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-07.5/RF-07.7 — aplicar suspensão/banimento e encerrar sanção antecipadamente. */
@Service
public class SanctionService {

  private final UsuarioRepository usuarioRepository;
  private final SancaoRepository sancaoRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuditService auditService;

  public SanctionService(
      UsuarioRepository usuarioRepository,
      SancaoRepository sancaoRepository,
      RefreshTokenRepository refreshTokenRepository,
      AuditService auditService) {
    this.usuarioRepository = usuarioRepository;
    this.sancaoRepository = sancaoRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.auditService = auditService;
  }

  /**
   * RF-07.5: transação única — sanção, status da conta, revogação de sessão e auditoria. Nada disso
   * interrompe entrega em andamento (RF-07.6/RN-11.3): esta operação só muda elegibilidade futura,
   * quem já está com um pedido aceito não é tocado aqui (isso é responsabilidade de T-13/T-15, que
   * consultam {@code usuario.status} só na próxima transição, não retroativamente).
   */
  @Transactional
  public SanctionSummary apply(UUID adminId, UUID targetUserId, CreateSanctionRequest request) {
    Usuario usuario = usuarioRepository.findById(targetUserId).orElseThrow(this::userNotFound);

    if (request.type() == SanctionType.SUSPENSION && request.expiresAt() == null) {
      throw new BadRequestException("MISSING_FIELD", "\"expiresAt\" é obrigatório para suspensão.");
    }
    if (request.type() == SanctionType.BAN && request.expiresAt() != null) {
      throw new BadRequestException("UNEXPECTED_FIELD", "\"expiresAt\" não se aplica a banimento.");
    }
    if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
      throw new BadRequestException("INVALID_FIELD", "\"expiresAt\" precisa estar no futuro.");
    }
    sancaoRepository
        .findBlockingSanction(targetUserId)
        .ifPresent(
            s -> {
              throw new ConflictException(
                  "USER_ALREADY_SANCTIONED", "Este usuário já está sob uma sanção ativa.");
            });

    Sancao sancao =
        new Sancao(
            UuidV7.next(),
            targetUserId,
            adminId,
            request.type(),
            request.reason(),
            request.expiresAt());
    sancaoRepository.save(sancao);

    if (request.type() == SanctionType.SUSPENSION) {
      usuario.suspender();
    } else {
      usuario.banir();
    }

    refreshTokenRepository.revogarTodosDoUsuario(targetUserId, Instant.now());

    String acao = request.type() == SanctionType.SUSPENSION ? "suspender_usuario" : "banir_usuario";
    auditService.record(adminId, acao, "usuario", targetUserId, request.reason());

    return toSummary(sancao);
  }

  /** RF-07.7: reativação antecipada — a expiração automática é {@link SanctionExpiryJob}. */
  @Transactional
  public void reactivate(UUID adminId, UUID sanctionId) {
    Sancao sancao =
        sancaoRepository
            .findById(sanctionId)
            .orElseThrow(
                () -> new NotFoundException("SANCTION_NOT_FOUND", "Sanção não encontrada."));
    if (!sancao.isAtiva()) {
      throw new ConflictException("SANCTION_ALREADY_INACTIVE", "Esta sanção já não está ativa.");
    }

    sancao.encerrar();
    usuarioRepository.findById(sancao.getUsuarioAlvoId()).ifPresent(Usuario::reativar);

    auditService.record(
        adminId, "reativar_usuario", "sancao", sanctionId, "Reativação antecipada pelo admin.");
  }

  private SanctionSummary toSummary(Sancao sancao) {
    return new SanctionSummary(
        sancao.getId(),
        sancao.getUsuarioAlvoId(),
        sancao.getTipo(),
        sancao.getMotivo(),
        sancao.getInicio(),
        sancao.getFim(),
        sancao.isAtiva(),
        sancao.getAdminId(),
        sancao.getCriadoEm());
  }

  private NotFoundException userNotFound() {
    return new NotFoundException("USER_NOT_FOUND", "Usuário não encontrado.");
  }
}
