package com.uaiou.orders.service;

import com.uaiou.blocks.service.BloqueioService;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.presence.service.CourierPresenceService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Revalidação do entregador dentro do lock — compartilhada entre T-13 (aceite direto) e T-14
 * (aceitar contraoferta), porque as duas transações concorrem pelo <strong>mesmo</strong> pedido e
 * precisam dizer "não" pelo mesmo motivo quando o estado mudou desde a listagem (RF-13.3).
 *
 * <p>Cada porta tem o seu próprio erro porque cada uma diz algo diferente ao app: bloqueado é
 * decisão do estabelecimento, indisponível/inativo é estado do próprio entregador.
 */
@Component
public class CourierEligibilityGuard {

  private final EntregadorRepository entregadorRepository;
  private final UsuarioRepository usuarioRepository;
  private final BloqueioService bloqueioService;
  private final CourierPresenceService courierPresenceService;

  public CourierEligibilityGuard(
      EntregadorRepository entregadorRepository,
      UsuarioRepository usuarioRepository,
      BloqueioService bloqueioService,
      CourierPresenceService courierPresenceService) {
    this.entregadorRepository = entregadorRepository;
    this.usuarioRepository = usuarioRepository;
    this.bloqueioService = bloqueioService;
    this.courierPresenceService = courierPresenceService;
  }

  public void ensureCanTransact(UUID entregadorId, Pedido pedido) {
    Entregador entregador =
        entregadorRepository
            .findById(entregadorId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "COURIER_NOT_FOUND", "Perfil de entregador não encontrado."));

    boolean contaAtiva =
        usuarioRepository
            .findById(entregadorId)
            .map(usuario -> usuario.getStatus() == UserStatus.ACTIVE)
            .orElse(false);
    if (!contaAtiva) {
      throw new ForbiddenException(
          "ACCOUNT_NOT_ACTIVE", "Só uma conta ativa pode transacionar neste pedido.");
    }
    if (!entregador.isDisponivel() || !courierPresenceService.hasFreshPresence(entregadorId)) {
      throw new ForbiddenException(
          "COURIER_NOT_AVAILABLE",
          "Fique disponível e envie sua posição atual antes de transacionar neste pedido.");
    }
    // RF-12.3: pode ter sido bloqueado entre a listagem e a ação.
    bloqueioService.requireNotBlocked(pedido.getEstabelecimentoId(), entregadorId);
  }
}
