package com.uaiou.orders.service;

import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.orders.OrderPublishedEvent;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF-11.10 / RF-08.10 — único evento 1:N do sistema: {@code order.published} vai para o resultado
 * da elegibilidade, calculado pelo MESMO motor da vitrine (T-11). Divergir aqui significaria
 * notificar quem não consegue ver o pedido — ou, pior, o contrário.
 *
 * <p>Entregador que fica disponível depois não recebe o push, mas vê o pedido na listagem: o
 * caminho garantido do marketplace é a listagem, não o push (RF-08.10).
 *
 * <p>{@code AFTER_COMMIT} + {@code REQUIRES_NEW}: a notificação é efeito do fato, não parte dele
 * (RF-08.1). Falha aqui não pode desfazer uma criação que já sucedeu — pedido publicado sem
 * notificação é degradação; pedido revertido por falha de notificação seria perda de trabalho do
 * cliente.
 */
@Component
public class OrderPublishedFanout {

  private static final Logger log = LoggerFactory.getLogger(OrderPublishedFanout.class);

  private final PedidoRepository pedidoRepository;
  private final OrderEligibilityService eligibilityService;
  private final NotificationService notificationService;

  public OrderPublishedFanout(
      PedidoRepository pedidoRepository,
      OrderEligibilityService eligibilityService,
      NotificationService notificationService) {
    this.pedidoRepository = pedidoRepository;
    this.eligibilityService = eligibilityService;
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoPublicar(OrderPublishedEvent evento) {
    pedidoRepository.findById(evento.pedidoId()).ifPresent(this::notificarElegiveis);
  }

  private void notificarElegiveis(Pedido pedido) {
    List<UUID> elegiveis = eligibilityService.entregadoresElegiveisPara(pedido);
    if (elegiveis.isEmpty()) {
      return;
    }

    notificationService.publicarParaVarios(
        elegiveis,
        NotificationType.ORDER_PUBLISHED,
        "Novo pedido disponível",
        "Pedido nº " + pedido.getNumero() + " em " + pedido.getDestBairro() + ".",
        // RF-08.7: só o necessário para o deep link abrir a tela certa — o resto o app busca com o
        // token do próprio usuário.
        Map.of("orderId", pedido.getId().toString(), "number", pedido.getNumero()));

    log.info(
        "order.published do pedido {} entregue a {} entregador(es) elegível(is).",
        pedido.getId(),
        elegiveis.size());
  }
}
