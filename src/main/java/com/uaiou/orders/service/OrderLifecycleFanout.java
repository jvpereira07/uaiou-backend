package com.uaiou.orders.service;

import com.uaiou.notifications.NotificationType;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.orders.OrderLifecycleEvents;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.uploads.service.UploadService;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF-26.6, RF-26.11, RF-26.19 e RF-26.28 — notificações de coleta, cancelamento e desistência,
 * sempre depois do commit: avisar sobre uma transição que ainda pode reverter manda o entregador
 * embora da loja por um cancelamento que não aconteceu.
 */
@Component
public class OrderLifecycleFanout {

  private final PedidoRepository pedidoRepository;
  private final UsuarioRepository usuarioRepository;
  private final EntregadorRepository entregadorRepository;
  private final NotificationService notificationService;
  private final UploadService uploadService;

  public OrderLifecycleFanout(
      PedidoRepository pedidoRepository,
      UsuarioRepository usuarioRepository,
      EntregadorRepository entregadorRepository,
      NotificationService notificationService,
      UploadService uploadService) {
    this.uploadService = uploadService;
    this.pedidoRepository = pedidoRepository;
    this.usuarioRepository = usuarioRepository;
    this.entregadorRepository = entregadorRepository;
    this.notificationService = notificationService;
  }

  /**
   * RF-26.6 — nome, placa e foto identificam o entregador quando há vários na porta. A URL da foto
   * vence em uma hora; o app que abrir o aviso depois disso mostra o ícone padrão.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoChegar(OrderLifecycleEvents.CourierArrived evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
      return;
    }
    String nome = nomeDe(evento.entregadorId(), "O entregador");
    Entregador entregador = entregadorRepository.findById(evento.entregadorId()).orElse(null);
    String placa = entregador == null ? null : entregador.getVeiculoPlaca();
    String foto = entregador == null ? null : uploadService.urlDeImagem(entregador.getFotoObjectKey());

    Map<String, Object> payload = new HashMap<>();
    payload.put("orderId", pedido.getId().toString());
    payload.put("number", pedido.getNumero());
    payload.put("courierId", evento.entregadorId().toString());
    payload.put("courierName", nome);
    if (placa != null) {
      payload.put("vehiclePlate", placa);
    }
    if (foto != null) {
      payload.put("courierPhotoUrl", foto);
    }
    payload.put("reminder", evento.reminder());

    String identificacao = placa == null ? nome : nome + " (placa " + placa + ")";
    notificationService.publicar(
        pedido.getEstabelecimentoId(),
        NotificationType.ORDER_COURIER_ARRIVED,
        evento.reminder() ? "Entregador aguardando o pacote" : "Entregador chegou",
        identificacao
            + " está no estabelecimento para retirar o pedido nº "
            + pedido.getNumero()
            + ". Entregue o pacote e confirme a coleta.",
        payload);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoColetar(OrderLifecycleEvents.PickedUp evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
      return;
    }
    notificationService.publicar(
        evento.entregadorId(),
        NotificationType.ORDER_PICKED_UP,
        "Coleta confirmada",
        "O estabelecimento confirmou a coleta do pedido nº "
            + pedido.getNumero()
            + ". Siga para a entrega.",
        Map.of("orderId", pedido.getId().toString(), "number", pedido.getNumero()));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoCancelar(OrderLifecycleEvents.Cancelled evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
      return;
    }

    if (evento.entregadorId() != null) {
      Map<String, Object> payload = new HashMap<>();
      payload.put("orderId", pedido.getId().toString());
      payload.put("number", pedido.getNumero());
      payload.put("reason", evento.reason().code());
      String corpo = "O estabelecimento cancelou o pedido nº " + pedido.getNumero() + ".";
      if (evento.fee() != null) {
        payload.put("fee", evento.fee().amount().toPlainString());
        corpo += " Você receberá R$ " + evento.fee().amount().toPlainString() + " de taxa.";
      }
      notificationService.publicar(
          evento.entregadorId(), NotificationType.ORDER_CANCELLED, "Pedido cancelado", corpo, payload);
    }

    for (UUID proponente : evento.proponentesInvalidados()) {
      notificationService.publicar(
          proponente,
          NotificationType.COUNTEROFFER_DECIDED,
          "Proposta não avaliada",
          "O pedido nº " + pedido.getNumero() + " foi cancelado pelo estabelecimento.",
          Map.of("orderId", pedido.getId().toString(), "outcome", "invalidated"));
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoDesistir(OrderLifecycleEvents.CourierWithdrew evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null) {
      return;
    }
    String nome = nomeDe(evento.entregadorId(), "O entregador");
    notificationService.publicar(
        pedido.getEstabelecimentoId(),
        NotificationType.ORDER_COURIER_WITHDREW,
        "Entregador desistiu",
        nome
            + " desistiu do pedido nº "
            + pedido.getNumero()
            + ". O pedido voltou a ser oferecido, sem custo para você.",
        Map.of(
            "orderId", pedido.getId().toString(),
            "number", pedido.getNumero(),
            "courierName", nome,
            "reason", evento.reason().code()));
  }

  private String nomeDe(UUID usuarioId, String padrao) {
    return usuarioRepository.findById(usuarioId).map(Usuario::getNomeExibicao).orElse(padrao);
  }
}
