package com.uaiou.orders.service;

import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.orders.ContingencyEscalatedEvent;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF-16.3 — sem push urgente aqui, o degrau 2 é inútil (o entregador fica parado sem ninguém saber
 * que ele está esperando). {@code DELIVERY_CODE_CONTINGENCY} já nasceu {@code mandatory} no
 * catálogo (T-08): o estabelecimento não pode silenciar este tipo.
 */
@Component
public class ContingencyEscalatedFanout {

  private final PedidoRepository pedidoRepository;
  private final NotificationService notificationService;

  public ContingencyEscalatedFanout(
      PedidoRepository pedidoRepository, NotificationService notificationService) {
    this.pedidoRepository = pedidoRepository;
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoEscalar(ContingencyEscalatedEvent evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
      return;
    }
    notificationService.publicar(
        pedido.getEstabelecimentoId(),
        NotificationType.DELIVERY_CODE_CONTINGENCY,
        "Entregador precisa do código",
        "O entregador do pedido nº "
            + pedido.getNumero()
            + " não conseguiu validar o código. Repasse pelo app até "
            + evento.prazoEm()
            + ".",
        Map.of(
            "orderId", pedido.getId().toString(),
            "number", pedido.getNumero(),
            "deadline", evento.prazoEm().toString()));
  }
}
