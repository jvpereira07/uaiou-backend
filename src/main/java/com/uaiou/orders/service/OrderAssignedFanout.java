package com.uaiou.orders.service;

import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.orders.OrderAssignedEvent;
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
 * RF-13.6 — efeitos pós-commit do aceite: {@code order.assigned} ao estabelecimento e {@code
 * counteroffer.decided} aos proponentes que ficaram pelo caminho.
 *
 * <p>Notificar os preteridos não é gentileza: RN-02.2 exige que o resultado volte ao entregador,
 * que está parado esperando resposta de uma proposta que não vai mais ser avaliada.
 *
 * <p>O despacho do código de entrega pelos canais (SMS ao recebedor, disponibilização ao
 * estabelecimento) é de T-15/T-16 e entra aqui quando aquelas tasks existirem — o código em si já
 * nasce nesta transação (RF-13.4).
 */
@Component
public class OrderAssignedFanout {

  private final PedidoRepository pedidoRepository;
  private final UsuarioRepository usuarioRepository;
  private final NotificationService notificationService;

  public OrderAssignedFanout(
      PedidoRepository pedidoRepository,
      UsuarioRepository usuarioRepository,
      NotificationService notificationService) {
    this.pedidoRepository = pedidoRepository;
    this.usuarioRepository = usuarioRepository;
    this.notificationService = notificationService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoAtribuir(OrderAssignedEvent evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
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
        nomeEntregador + " aceitou o pedido nº " + pedido.getNumero() + ".",
        Map.of("orderId", pedido.getId().toString(), "number", pedido.getNumero()));

    for (var proponente : evento.proponentesInvalidados()) {
      notificationService.publicar(
          proponente,
          NotificationType.COUNTEROFFER_DECIDED,
          "Proposta não avaliada",
          "O pedido nº " + pedido.getNumero() + " foi aceito por outro entregador.",
          Map.of("orderId", pedido.getId().toString(), "outcome", "invalidated"));
    }
  }
}
