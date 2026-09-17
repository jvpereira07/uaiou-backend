package com.uaiou.orders.service;

import com.uaiou.delivery.service.GeofenceEvaluator;
import com.uaiou.orders.OrderLifecycleEvents;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.config.PickupProperties;
import com.uaiou.orders.dto.OrderLifecycleRequests.PickupArrivalRequest;
import com.uaiou.orders.dto.OrderLifecycleResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.presence.CourierLocationUpdatedEvent;
import com.uaiou.presence.service.CourierPresenceService;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.error.TooManyRequestsException;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-26.1 a RF-26.13 — chegada ao estabelecimento e coleta.
 *
 * <p>Toda transição trava o pedido ({@code SELECT ... FOR UPDATE}) antes de decidir: confirmação de
 * coleta, cancelamento e desistência disputam o mesmo pedido, e só o lock garante que no máximo uma
 * vença (RF-26.15). A detecção automática também trava — sem isso, a gravação de {@code chegou_em}
 * reescreveria um status que outra transação acabou de mudar.
 */
@Service
public class PickupService {

  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final CourierPresenceService courierPresenceService;
  private final GeofenceEvaluator geofenceEvaluator;
  private final PickupProperties properties;
  private final ApplicationEventPublisher events;

  public PickupService(
      PedidoRepository pedidoRepository,
      EntregadorRepository entregadorRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      CourierPresenceService courierPresenceService,
      GeofenceEvaluator geofenceEvaluator,
      PickupProperties properties,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.courierPresenceService = courierPresenceService;
    this.geofenceEvaluator = geofenceEvaluator;
    this.properties = properties;
    this.events = events;
  }

  /**
   * RF-26.1/RF-26.2 — ouvinte síncrono de {@code PUT /me/location}, na mesma transação. Consulta só
   * ids de pedidos aguardando chegada deste entregador (RNF-26.1): o caso comum é lista vazia e
   * nenhum custo além de uma consulta indexada.
   */
  @EventListener
  public void aoAtualizarPosicao(CourierLocationUpdatedEvent evento) {
    List<UUID> aguardando =
        pedidoRepository.idsAguardandoChegada(evento.courierId(), OrderStatus.ACCEPTED);
    if (aguardando.isEmpty()) {
      return;
    }
    Entregador entregador = entregadorRepository.findById(evento.courierId()).orElse(null);
    if (entregador == null || entregador.getLat() == null || entregador.getLongitude() == null) {
      return;
    }

    for (UUID pedidoId : aguardando) {
      Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElse(null);
      if (pedido == null || !pedido.estaAtribuidoA(evento.courierId())) {
        continue;
      }
      Estabelecimento loja = lojaDe(pedido);
      if (!temCoordenada(loja)) {
        // RF-26.5 — sem coordenada não há detecção; a coleta segue possível sem checagem de raio.
        continue;
      }
      boolean dentro =
          distanciaAteLoja(entregador.getLat(), entregador.getLongitude(), loja)
              <= properties.radiusMeters();
      if (pedido.registrarLeituraNoRaioColeta(dentro, properties.requiredReadings())) {
        events.publishEvent(
            new OrderLifecycleEvents.CourierArrived(pedidoId, evento.courierId(), false));
      }
    }
  }

  /** RF-26.3 — "Cheguei": mesma regra de raio, aplicada à posição enviada. */
  @Transactional
  public OrderLifecycleResponse registrarChegada(
      UUID entregadorId, UUID pedidoId, PickupArrivalRequest request) {
    Pedido pedido = travarAtribuido(entregadorId, pedidoId);
    exigirAguardandoColeta(pedido);

    Estabelecimento loja = lojaDe(pedido);
    if (!temCoordenada(loja)) {
      throw new BusinessRuleException(
          "PICKUP_LOCATION_UNKNOWN",
          "O estabelecimento ainda não marcou a localização no mapa; a coleta pode ser confirmada"
              + " sem registrar a chegada.",
          "RF-26.5");
    }
    double distancia = distanciaAteLoja(request.lat(), request.lng(), loja);
    if (distancia > properties.radiusMeters()) {
      throw new BusinessRuleException(
          "OUTSIDE_PICKUP_RADIUS",
          "Você precisa estar no estabelecimento para registrar a chegada.",
          "RF-26.3",
          Map.of(
              "distanceMeters", Math.round(distancia),
              "radiusMeters", properties.radiusMeters()));
    }

    if (pedido.registrarChegada()) {
      events.publishEvent(new OrderLifecycleEvents.CourierArrived(pedidoId, entregadorId, false));
    }
    return toResponse(pedido, null);
  }

  /** RF-26.7/RF-26.8/RF-26.9 — só o estabelecimento confirma, e nunca por decurso de prazo. */
  @Transactional
  public OrderLifecycleResponse confirmarColeta(UUID estabelecimentoId, UUID pedidoId) {
    Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElseThrow(this::notFound);
    if (!pedido.pertenceAoEstabelecimento(estabelecimentoId)) {
      throw notFound();
    }
    exigirAguardandoColeta(pedido);

    if (!podeConfirmarColeta(pedido, lojaDe(pedido))) {
      throw new BusinessRuleException(
          "COURIER_NOT_AT_PICKUP", "O entregador ainda não chegou ao estabelecimento.", "RF-26.8");
    }

    pedido.confirmarColeta();
    events.publishEvent(new OrderLifecycleEvents.PickedUp(pedidoId, pedido.getEntregadorId()));
    return toResponse(pedido, null);
  }

  /** RF-26.10 — reaviso pedido pelo entregador que está esperando na loja. */
  @Transactional
  public OrderLifecycleResponse pedirNovoAviso(UUID entregadorId, UUID pedidoId) {
    Pedido pedido = travarAtribuido(entregadorId, pedidoId);
    exigirAguardandoColeta(pedido);
    if (pedido.getChegouEm() == null) {
      throw new BusinessRuleException(
          "COURIER_NOT_ARRIVED",
          "Registre a chegada ao estabelecimento antes de pedir novo aviso.",
          "RF-26.10");
    }

    Instant agora = Instant.now();
    Instant liberadoEm = pedido.getChegouEm().plus(properties.reminderAfter());
    if (agora.isBefore(liberadoEm)) {
      throw new BusinessRuleException(
          "REMINDER_TOO_EARLY",
          "Aguarde um pouco: o estabelecimento acabou de ser avisado.",
          "RF-26.10",
          Map.of("availableAt", liberadoEm.toString()));
    }
    if (pedido.getLembreteColetaEm() != null) {
      Instant proximo = pedido.getLembreteColetaEm().plus(properties.reminderMinInterval());
      if (agora.isBefore(proximo)) {
        throw new TooManyRequestsException(
            "REMINDER_TOO_FREQUENT",
            "O estabelecimento foi avisado há pouco. Tente de novo em instantes.",
            Map.of("retryAt", proximo.toString()));
      }
    }

    pedido.registrarLembreteColeta();
    events.publishEvent(new OrderLifecycleEvents.CourierArrived(pedidoId, entregadorId, true));
    return toResponse(pedido, null);
  }

  /**
   * RF-26.8 — a regra única da confirmação, reusada pelos {@code _links} (RF-26.13): com coordenada
   * da loja, exige chegada registrada e posição fresca do entregador dentro do raio; sem
   * coordenada, não há o que checar (RF-26.5).
   */
  @Transactional(readOnly = true)
  public boolean podeConfirmarColeta(Pedido pedido, Estabelecimento loja) {
    if (!pedido.aguardandoColeta()) {
      return false;
    }
    if (!temCoordenada(loja)) {
      return true;
    }
    if (pedido.getChegouEm() == null) {
      return false;
    }
    Entregador entregador = entregadorRepository.findById(pedido.getEntregadorId()).orElse(null);
    return entregador != null
        && courierPresenceService.hasFreshPosition(entregador)
        && distanciaAteLoja(entregador.getLat(), entregador.getLongitude(), loja)
            <= properties.radiusMeters();
  }

  /**
   * RF-26.17/RF-26.18 — a taxa que o estabelecimento pagaria cancelando agora. Nulo quando não há
   * taxa: pedido fora da janela, entregador ainda não chegou (D5) ou percentual zerado.
   */
  public Money taxaSeCancelarAgora(Pedido pedido) {
    if (!pedido.aguardandoColeta()
        || pedido.getChegouEm() == null
        || pedido.getFreteFinal() == null) {
      return null;
    }
    BigDecimal valor =
        pedido
            .getFreteFinal()
            .amount()
            .multiply(properties.arrivedCancellationFeeRate())
            .setScale(2, RoundingMode.HALF_UP);
    return valor.signum() > 0 ? Money.of(valor) : null;
  }

  public BigDecimal taxaPercentualDeCancelamento() {
    return properties.arrivedCancellationFeeRate();
  }

  public boolean temCoordenada(Estabelecimento loja) {
    return loja != null && loja.getLat() != null && loja.getLongitude() != null;
  }

  private double distanciaAteLoja(BigDecimal lat, BigDecimal lng, Estabelecimento loja) {
    return geofenceEvaluator.distanciaEmMetros(lat, lng, loja.getLat(), loja.getLongitude());
  }

  private Estabelecimento lojaDe(Pedido pedido) {
    return estabelecimentoRepository.findById(pedido.getEstabelecimentoId()).orElse(null);
  }

  private Pedido travarAtribuido(UUID entregadorId, UUID pedidoId) {
    Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElseThrow(this::notFound);
    if (!pedido.estaAtribuidoA(entregadorId)) {
      throw new ForbiddenException(
          "NOT_ASSIGNED_COURIER", "Só o entregador atribuído age sobre esta coleta.");
    }
    return pedido;
  }

  private void exigirAguardandoColeta(Pedido pedido) {
    if (!pedido.aguardandoColeta()) {
      throw new ConflictException(
          "ORDER_NOT_AWAITING_PICKUP", "Este pedido não está aguardando coleta.");
    }
  }

  static OrderLifecycleResponse toResponse(Pedido pedido, Money taxa) {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("order", LinkRef.get("/api/v1/orders/" + pedido.getId()));
    return new OrderLifecycleResponse(
        pedido.getId(),
        pedido.getStatus(),
        pedido.getChegouEm(),
        pedido.getColetadoEm(),
        pedido.getCanceladoEm(),
        taxa,
        links);
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
