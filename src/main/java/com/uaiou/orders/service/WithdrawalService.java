package com.uaiou.orders.service;

import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.orders.OrderLifecycleEvents;
import com.uaiou.orders.OrderPublishedEvent;
import com.uaiou.orders.WithdrawalReason;
import com.uaiou.orders.config.PickupProperties;
import com.uaiou.orders.dto.OrderLifecycleRequests.WithdrawalRequest;
import com.uaiou.orders.dto.OrderLifecycleResponse;
import com.uaiou.orders.entity.DesistenciaPedido;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.DesistenciaPedidoRepository;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-26.22 a RF-26.31 — desistência do entregador, entre o aceite e a coleta. */
@Service
public class WithdrawalService {

  private final PedidoRepository pedidoRepository;
  private final DesistenciaPedidoRepository desistenciaRepository;
  private final OtpRepository otpRepository;
  private final PickupProperties properties;
  private final ApplicationEventPublisher events;

  public WithdrawalService(
      PedidoRepository pedidoRepository,
      DesistenciaPedidoRepository desistenciaRepository,
      OtpRepository otpRepository,
      PickupProperties properties,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.desistenciaRepository = desistenciaRepository;
    this.otpRepository = otpRepository;
    this.properties = properties;
    this.events = events;
  }

  /**
   * RF-26.23/RF-26.24 — o pedido volta a "publicado" sem custo ao estabelecimento: nenhum crédito
   * consumido, nenhum lançamento. O código de entrega é descartado — o próximo aceite gera outro e
   * o reenvia (RF-13.4) —, porque o anterior já foi entregue ao recebedor junto com a expectativa
   * de que <em>este</em> entregador chegaria.
   */
  @Transactional
  public OrderLifecycleResponse desistir(
      UUID entregadorId, UUID pedidoId, WithdrawalRequest request) {
    String nota =
        OrderCancellationService.notaValidada(
            request.reason() == WithdrawalReason.OTHER, request.note());

    Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElseThrow(this::notFound);
    if (!pedido.estaAtribuidoA(entregadorId)) {
      throw new ForbiddenException(
          "NOT_ASSIGNED_COURIER", "Só o entregador atribuído desiste desta entrega.");
    }
    if (!pedido.aguardandoColeta()) {
      throw new ConflictException(
          "ORDER_NOT_WITHDRAWABLE",
          "Não é possível desistir: o pacote já foi coletado ou a entrega terminou.");
    }

    desistenciaRepository.save(
        new DesistenciaPedido(
            UuidV7.next(),
            pedidoId,
            entregadorId,
            request.reason().code(),
            nota,
            pedido.getAceitoEm(),
            pedido.getChegouEm(),
            contaPenalidade(request.reason(), pedido.getChegouEm())));

    otpRepository.deleteByPedidoId(pedidoId);
    pedido.desfazerAceite();

    events.publishEvent(
        new OrderLifecycleEvents.CourierWithdrew(pedidoId, entregadorId, request.reason()));
    // RF-26.28 — novo fan-out de elegibilidade; o que desistiu é excluído pelo motor.
    events.publishEvent(new OrderPublishedEvent(pedidoId));

    return PickupService.toResponse(pedido, null);
  }

  /**
   * RF-26.30 — atraso da loja não penaliza: desistir por {@code pickup_delay} depois de esperar
   * além do tolerado é desistência justa.
   */
  private boolean contaPenalidade(WithdrawalReason motivo, Instant chegouEm) {
    if (motivo != WithdrawalReason.PICKUP_DELAY || chegouEm == null) {
      return true;
    }
    Instant toleradoAte = chegouEm.plus(properties.withdrawalPickupDelayTolerance());
    return Instant.now().isBefore(toleradoAte);
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
