package com.uaiou.orders.service;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.counteroffers.entity.Contraoferta;
import com.uaiou.counteroffers.repository.ContraofertaRepository;
import com.uaiou.delivery.entity.Otp;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.orders.OrderLifecycleEvents;
import com.uaiou.orders.OrderLifecycleEvents.InterventionOrigin;
import com.uaiou.orders.OrderLifecycleEvents.InterventionOutcome;
import com.uaiou.orders.entity.Pedido;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Efeitos colaterais comuns a toda intervenção da plataforma (admin ou timeout) sobre um pedido em
 * andamento. O chamador já segura o lock do pedido e já validou a transição — aqui só se aplica o
 * efeito e seus satélites (contraofertas, código de entrega, notificação).
 *
 * <p>Sem taxa de cancelamento (RF-26.17): a taxa compensa o entregador por um cancelamento do
 * estabelecimento, e aqui quem cancela é a plataforma. Compensação, quando cabível, é ajuste
 * financeiro explícito do admin (RF-09.10).
 */
@Service
public class PlatformInterventionService {

  /** {@code pedido.cancelamento_motivo} das intervenções — fora de {@code CancellationReason}. */
  public static final String MOTIVO_ADMIN = "admin";

  public static final String MOTIVO_TIMEOUT = "timeout";

  private final ContraofertaRepository contraofertaRepository;
  private final OtpRepository otpRepository;
  private final ApplicationEventPublisher events;

  public PlatformInterventionService(
      ContraofertaRepository contraofertaRepository,
      OtpRepository otpRepository,
      ApplicationEventPublisher events) {
    this.contraofertaRepository = contraofertaRepository;
    this.otpRepository = otpRepository;
    this.events = events;
  }

  @Transactional
  public void cancelar(Pedido pedido, InterventionOrigin origem, String nota) {
    UUID entregadorId = pedido.getEntregadorId();
    List<UUID> proponentes = invalidarContraofertasPendentes(pedido.getId());
    otpRepository.findByPedidoId(pedido.getId()).ifPresent(Otp::expirar);

    pedido.cancelar(origem == InterventionOrigin.ADMIN ? MOTIVO_ADMIN : MOTIVO_TIMEOUT, nota);

    events.publishEvent(
        new OrderLifecycleEvents.PlatformIntervention(
            pedido.getId(),
            pedido.getEstabelecimentoId(),
            entregadorId,
            origem,
            InterventionOutcome.CANCELLED,
            proponentes));
  }

  /**
   * Mesmo tratamento do código que a desistência (RF-26.28): descartado, porque a UNIQUE de {@code
   * otp} exige que o próximo aceite gere outro — e o antigo não pode continuar válido na mão de
   * quem já não responde pelo pedido.
   */
  @Transactional
  public void devolverAVitrine(Pedido pedido, InterventionOrigin origem) {
    UUID entregadorId = pedido.getEntregadorId();
    otpRepository.deleteByPedidoId(pedido.getId());

    pedido.devolverAVitrine();

    events.publishEvent(
        new OrderLifecycleEvents.PlatformIntervention(
            pedido.getId(),
            pedido.getEstabelecimentoId(),
            entregadorId,
            origem,
            InterventionOutcome.RETURNED_TO_SHOWCASE,
            List.of()));
  }

  private List<UUID> invalidarContraofertasPendentes(UUID pedidoId) {
    List<Contraoferta> pendentes =
        contraofertaRepository.findByPedidoIdAndStatus(pedidoId, CounterofferStatus.PENDING);
    pendentes.forEach(Contraoferta::invalidar);
    return pendentes.stream().map(Contraoferta::getEntregadorId).distinct().toList();
  }
}
