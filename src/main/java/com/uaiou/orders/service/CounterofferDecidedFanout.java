package com.uaiou.orders.service;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.orders.CounterofferDecidedEvent;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF-14.6/RF-14.7/RF-14.8 — efeitos pós-commit da decisão sobre uma contraoferta.
 *
 * <p>No aceite, o estabelecimento também recebe {@code order.assigned}, mesma notificação de T-13:
 * do ponto de vista de quem publicou o pedido, "aceito direto" e "aceito por contraoferta" são o
 * mesmo fato observável.
 */
@Component
public class CounterofferDecidedFanout {

  private final PedidoRepository pedidoRepository;
  private final UsuarioRepository usuarioRepository;
  private final NotificationService notificationService;

  public CounterofferDecidedFanout(
      PedidoRepository pedidoRepository,
      UsuarioRepository usuarioRepository,
      NotificationService notificationService) {
    this.pedidoRepository = pedidoRepository;
    this.usuarioRepository = usuarioRepository;
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoDecidir(CounterofferDecidedEvent evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
      return;
    }

    boolean aceita = evento.outcome() == CounterofferStatus.ACCEPTED;
    notificationService.publicar(
        evento.entregadorId(),
        NotificationType.COUNTEROFFER_DECIDED,
        aceita ? "Contraoferta aceita" : "Contraoferta recusada",
        "Sua proposta para o pedido nº "
            + pedido.getNumero()
            + " foi "
            + (aceita ? "aceita." : "recusada."),
        Map.of(
            "orderId", pedido.getId().toString(),
            "counterofferId", evento.contraofertaId().toString(),
            "outcome", aceita ? "accepted" : "rejected"));

    if (!aceita) {
      return;
    }

    String nomeEntregador =
        usuarioRepository
            .findById(evento.entregadorId())
            .map(Usuario::getNomeExibicao)
            .orElse("Um entregador");
    notificationService.publicar(
        pedido.getEstabelecimentoId(),
        NotificationType.ORDER_ASSIGNED,
        "Pedido aceito",
        nomeEntregador + " aceitou o pedido nº " + pedido.getNumero() + " pela contraoferta.",
        Map.of("orderId", pedido.getId().toString(), "number", pedido.getNumero()));

    for (var proponente : evento.proponentesInvalidados()) {
      notificationService.publicar(
          proponente,
          NotificationType.COUNTEROFFER_DECIDED,
          "Proposta não avaliada",
          "O pedido nº " + pedido.getNumero() + " foi aceito por outra proposta.",
          Map.of("orderId", pedido.getId().toString(), "outcome", "invalidated"));
    }
  }
}
