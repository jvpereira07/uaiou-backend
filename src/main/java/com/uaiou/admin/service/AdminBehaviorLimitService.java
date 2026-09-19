package com.uaiou.admin.service;

import com.uaiou.admin.dto.AdminOrderDetail;
import com.uaiou.admin.dto.BehaviorLimitResponse;
import com.uaiou.admin.dto.UpdateBehaviorLimitRequest;
import com.uaiou.orders.limits.BehaviorLimitRule;
import com.uaiou.orders.limits.LimiteComportamento;
import com.uaiou.orders.limits.LimiteComportamentoRepository;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calibração dos limites de comportamento pelo painel; toda alteração vai para a auditoria. */
@Service
public class AdminBehaviorLimitService {

  private final LimiteComportamentoRepository repository;
  private final UsuarioRepository usuarioRepository;
  private final AuditService auditService;

  public AdminBehaviorLimitService(
      LimiteComportamentoRepository repository,
      UsuarioRepository usuarioRepository,
      AuditService auditService) {
    this.repository = repository;
    this.usuarioRepository = usuarioRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<BehaviorLimitResponse> list() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(l -> l.getRegra().ordinal()))
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public BehaviorLimitResponse update(
      UUID adminId, BehaviorLimitRule regra, UpdateBehaviorLimitRequest request) {
    LimiteComportamento limite =
        repository
            .findById(regra.dbKey())
            .orElseThrow(() -> new NotFoundException("LIMIT_NOT_FOUND", "Limite não encontrado."));
    limite.atualizar(
        adminId, request.max(), request.windowMinutes(), request.blockMinutes(), request.active());
    auditService.record(
        adminId,
        "behavior_limit_updated",
        null,
        null,
        regra.code()
            + ": "
            + request.max()
            + " em "
            + request.windowMinutes()
            + " min → bloqueio de "
            + request.blockMinutes()
            + " min, "
            + (request.active() ? "ligado" : "desligado"));
    return toResponse(limite);
  }

  private BehaviorLimitResponse toResponse(LimiteComportamento limite) {
    UUID autorId = limite.getAtualizadoPor();
    AdminOrderDetail.Ref autor =
        autorId == null
            ? null
            : usuarioRepository
                .findById(autorId)
                .map(Usuario::getNomeExibicao)
                .map(nome -> new AdminOrderDetail.Ref(autorId, nome))
                .orElse(null);
    return new BehaviorLimitResponse(
        limite.getRegra(),
        limite.getMaximo(),
        limite.getJanelaMinutos(),
        limite.getBloqueioMinutos(),
        limite.isAtivo(),
        limite.getAtualizadoEm(),
        autor);
  }
}
