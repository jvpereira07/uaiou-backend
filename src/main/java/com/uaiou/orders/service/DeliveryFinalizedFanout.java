package com.uaiou.orders.service;

import com.uaiou.delivery.FinalizationType;
import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.orders.DeliveryFinalizedEvent;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF-15.12 — {@code delivery.completed} ao estabelecimento (finalização por código). RF-17.7 —
 * {@code delivery.contestable} ao estabelecimento (finalização contestável); é esse evento que
 * dispara a janela de contestação por SUPORTE, não por rota de disputa (T-17 é explícito: nenhuma
 * rota de arbitragem na v1).
 *
 * <p>Abertura de janela de avaliação (T-19) e recálculo de score (T-20) ficam de fora: tasks que
 * ainda não existem neste backlog.
 */
@Component
public class DeliveryFinalizedFanout {

  private final PedidoRepository pedidoRepository;
  private final NotificationService notificationService;

  public DeliveryFinalizedFanout(
      PedidoRepository pedidoRepository, NotificationService notificationService) {
    this.pedidoRepository = pedidoRepository;
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoFinalizar(DeliveryFinalizedEvent evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
      return;
    }

    if (evento.tipo() == FinalizationType.CODE) {
      notificationService.publicar(
          pedido.getEstabelecimentoId(),
          NotificationType.DELIVERY_COMPLETED,
          "Entrega concluída",
          "O pedido nº " + pedido.getNumero() + " foi entregue.",
          Map.of("orderId", pedido.getId().toString(), "number", pedido.getNumero()));
    } else {
      notificationService.publicar(
          pedido.getEstabelecimentoId(),
          NotificationType.DELIVERY_CONTESTABLE,
          "Entrega finalizada sem código",
          "O pedido nº "
              + pedido.getNumero()
              + " foi finalizado com evidência alternativa. Para contestar, abra um chamado de"
              + " suporte referenciando este pedido.",
          Map.of("orderId", pedido.getId().toString(), "number", pedido.getNumero()));
    }
  }
}
