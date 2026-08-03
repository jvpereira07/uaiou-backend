package com.uaiou.orders.service;

import com.uaiou.orders.OrderPublishedEvent;
import com.uaiou.orders.repository.PedidoRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * RF-11.10 — resolve <em>quem</em> deve receber {@code order.published} depois que a criação
 * commitou.
 *
 * <p><strong>Entrega da notificação é de T-08, que não foi construída</strong> (priorização do
 * dono). O que é de T-11 — e o que o critério de aceite 10 mede — é o <em>conjunto de
 * destinatários</em>: "emitido apenas aos elegíveis". Esse conjunto é calculado aqui pelo mesmo
 * motor da vitrine, e exposto por {@link #ultimosDestinatarios} para o teste conseguir afirmar
 * sobre ele sem depender de infraestrutura de notificação que ainda não existe.
 *
 * <p>Quando T-08 entrar, o corpo do listener troca o log por uma chamada ao serviço de notificação;
 * a seleção não muda.
 *
 * <p>{@code AFTER_COMMIT} e {@code REQUIRES_NEW}: o fan-out lê o pedido já commitado, e uma falha
 * aqui não pode desfazer uma criação que já sucedeu — pedido publicado sem notificação é
 * degradação; pedido revertido porque a notificação falhou seria perda de trabalho do cliente.
 */
@Component
public class OrderPublishedFanout {

  private static final Logger log = LoggerFactory.getLogger(OrderPublishedFanout.class);

  private final PedidoRepository pedidoRepository;
  private final OrderEligibilityService eligibilityService;

  private volatile List<UUID> ultimosDestinatarios = List.of();

  public OrderPublishedFanout(
      PedidoRepository pedidoRepository, OrderEligibilityService eligibilityService) {
    this.pedidoRepository = pedidoRepository;
    this.eligibilityService = eligibilityService;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void aoPublicar(OrderPublishedEvent evento) {
    pedidoRepository
        .findById(evento.pedidoId())
        .ifPresent(
            pedido -> {
              List<UUID> elegiveis = eligibilityService.entregadoresElegiveisPara(pedido);
              this.ultimosDestinatarios = elegiveis;
              log.info(
                  "order.published do pedido {} destinado a {} entregador(es) elegível(is)"
                      + " — entrega efetiva da notificação é de T-08.",
                  pedido.getId(),
                  elegiveis.size());
            });
  }

  /** Só para verificação (critério de aceite 10) enquanto T-08 não existe. */
  public List<UUID> ultimosDestinatarios() {
    return ultimosDestinatarios;
  }
}
