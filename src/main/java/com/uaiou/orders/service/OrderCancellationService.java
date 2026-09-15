package com.uaiou.orders.service;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.counteroffers.entity.Contraoferta;
import com.uaiou.counteroffers.repository.ContraofertaRepository;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.entity.Otp;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.orders.CancellationReason;
import com.uaiou.orders.OrderLifecycleEvents;
import com.uaiou.orders.dto.OrderLifecycleRequests.CancellationRequest;
import com.uaiou.orders.dto.OrderLifecycleResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.money.Money;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-26.14 a RF-26.20 — cancelamento pelo estabelecimento, até a coleta. */
@Service
public class OrderCancellationService {

  private final PedidoRepository pedidoRepository;
  private final ContraofertaRepository contraofertaRepository;
  private final OtpRepository otpRepository;
  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final PickupService pickupService;
  private final ApplicationEventPublisher events;

  public OrderCancellationService(
      PedidoRepository pedidoRepository,
      ContraofertaRepository contraofertaRepository,
      OtpRepository otpRepository,
      LancamentoFreteRepository lancamentoFreteRepository,
      PickupService pickupService,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.contraofertaRepository = contraofertaRepository;
    this.otpRepository = otpRepository;
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.pickupService = pickupService;
    this.events = events;
  }

  /**
   * Tudo na mesma transação e depois do lock (RF-26.15): status, taxa, contraofertas e código. A
   * taxa é decidida pelo que o pedido é <em>dentro</em> do lock — a chegada gravada por uma posição
   * que chegou um instante antes já conta.
   *
   * <p>RF-26.20 — créditos de postagem não são estornados: pagaram a publicação, que aconteceu.
   */
  @Transactional
  public OrderLifecycleResponse cancelar(
      UUID estabelecimentoId, UUID pedidoId, CancellationRequest request) {
    String nota = notaValidada(request.reason() == CancellationReason.OTHER, request.note());

    Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElseThrow(this::notFound);
    if (!pedido.pertenceAoEstabelecimento(estabelecimentoId)) {
      throw notFound();
    }
    if (!pedido.podeSerCancelado()) {
      throw new ConflictException(
          "ORDER_NOT_CANCELLABLE",
          "Este pedido não pode mais ser cancelado: o entregador já coletou o pacote ou a entrega"
              + " terminou.");
    }

    UUID entregadorId = pedido.getEntregadorId();
    Money taxa = pickupService.taxaSeCancelarAgora(pedido);
    if (taxa != null) {
      lancamentoFreteRepository.save(
          LancamentoFrete.taxaDeCancelamento(
              UuidV7.next(),
              pedidoId,
              entregadorId,
              estabelecimentoId,
              taxa,
              pickupService.taxaPercentualDeCancelamento()));
    }

    List<Contraoferta> pendentes =
        contraofertaRepository.findByPedidoIdAndStatus(pedidoId, CounterofferStatus.PENDING);
    pendentes.forEach(Contraoferta::invalidar);
    List<UUID> proponentes = pendentes.stream().map(Contraoferta::getEntregadorId).distinct().toList();

    otpRepository.findByPedidoId(pedidoId).ifPresent(Otp::expirar);

    pedido.cancelar(request.reason().code(), nota);

    events.publishEvent(
        new OrderLifecycleEvents.Cancelled(
            pedidoId, entregadorId, request.reason(), taxa, proponentes));

    return PickupService.toResponse(pedido, taxa);
  }

  static String notaValidada(boolean exigida, String nota) {
    String limpa = nota == null || nota.isBlank() ? null : nota.strip();
    if (exigida && limpa == null) {
      throw new BusinessRuleException(
          "NOTE_REQUIRED", "Descreva o motivo quando escolher \"outro\".", "RF-26.16");
    }
    return limpa;
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
