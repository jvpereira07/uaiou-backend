package com.uaiou.orders.service;

import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.orders.CounterofferCreatedEvent;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** RF-14.4 — {@code counteroffer.received} ao estabelecimento, pós-commit. */
@Component
public class CounterofferCreatedFanout {

  private final PedidoRepository pedidoRepository;
  private final NotificationService notificationService;

  public CounterofferCreatedFanout(
      PedidoRepository pedidoRepository, NotificationService notificationService) {
    this.pedidoRepository = pedidoRepository;
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoReceber(CounterofferCreatedEvent evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
      return;
    }
    notificationService.publicar(
        pedido.getEstabelecimentoId(),
        NotificationType.COUNTEROFFER_RECEIVED,
        "Nova contraoferta",
        "Um entregador propôs outro valor para o pedido nº " + pedido.getNumero() + ".",
        Map.of(
            "orderId", pedido.getId().toString(),
            "counterofferId", evento.contraofertaId().toString()));
  }
}
