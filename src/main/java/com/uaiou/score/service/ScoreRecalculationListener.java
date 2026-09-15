package com.uaiou.score.service;

import com.uaiou.orders.DeliveryFinalizedEvent;
import com.uaiou.orders.OrderLifecycleEvents;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.reviews.ReviewCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF-19.9/RF-20.3 — recálculo por evento-insumo, nunca na leitura. Uma avaliação criada recalcula o
 * alvo; uma entrega finalizada recalcula a taxa de conclusão do entregador. A taxa de contingência
 * do estabelecimento é recalculada pelo próprio {@link
 * com.uaiou.delivery.service.ContingencyExpiryJob} no momento em que resolve o prazo — o insumo
 * (penalidade ou ausência dela) só existe ali.
 */
@Component
public class ScoreRecalculationListener {

  private final ScoreCalculationService scoreCalculationService;
  private final PedidoRepository pedidoRepository;

  public ScoreRecalculationListener(
      ScoreCalculationService scoreCalculationService, PedidoRepository pedidoRepository) {
    this.scoreCalculationService = scoreCalculationService;
    this.pedidoRepository = pedidoRepository;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoAvaliar(ReviewCreatedEvent evento) {
    if (evento.targetIsCourier()) {
      scoreCalculationService.recalcularEntregador(evento.targetId());
    } else {
      scoreCalculationService.recalcularEstabelecimento(evento.targetId());
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoFinalizar(DeliveryFinalizedEvent evento) {
    Pedido pedido = pedidoRepository.findById(evento.pedidoId()).orElse(null);
    if (pedido == null || pedido.getEntregadorId() == null) {
      return;
    }
    scoreCalculationService.recalcularEntregador(pedido.getEntregadorId());
  }

  /** RF-26.31 — desistência é insumo da taxa de conclusão. */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoDesistir(OrderLifecycleEvents.CourierWithdrew evento) {
    scoreCalculationService.recalcularEntregador(evento.entregadorId());
  }
}
