package com.uaiou.orders.service;

import com.uaiou.delivery.DeliveryCodeStatus;
import com.uaiou.delivery.FinalizationType;
import com.uaiou.delivery.config.DeliveryProperties;
import com.uaiou.delivery.entity.EvidenciaEntrega;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.entity.Otp;
import com.uaiou.delivery.repository.EvidenciaEntregaRepository;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.delivery.service.DeliveryAttemptRecorder;
import com.uaiou.delivery.service.DeliveryCodeService;
import com.uaiou.delivery.service.GeofenceEvaluator;
import com.uaiou.orders.DeliveryFinalizedEvent;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.dto.DeliveryCompletionRequest;
import com.uaiou.orders.dto.DeliveryCompletionResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.uploads.Purpose;
import com.uaiou.uploads.entity.Upload;
import com.uaiou.uploads.service.UploadService;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code POST /orders/{id}/delivery/completion} — um recurso, dois modos (RF-15.6/RF-17.2). O
 * servidor decide a legalidade de cada um; nenhum dos dois debita saldo (RF-15.10/RF-17.5,
 * escopo-v1.md) — o efeito financeiro da v1 é só o registro em {@code lancamento_frete}.
 */
@Service
public class DeliveryCompletionService {

  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;
  private final OtpRepository otpRepository;
  private final EvidenciaEntregaRepository evidenciaEntregaRepository;
  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final UploadService uploadService;
  private final GeofenceEvaluator geofenceEvaluator;
  private final DeliveryAttemptRecorder attemptRecorder;
  private final DeliveryProperties properties;
  private final ApplicationEventPublisher events;

  public DeliveryCompletionService(
      PedidoRepository pedidoRepository,
      EntregadorRepository entregadorRepository,
      OtpRepository otpRepository,
      EvidenciaEntregaRepository evidenciaEntregaRepository,
      LancamentoFreteRepository lancamentoFreteRepository,
      UploadService uploadService,
      GeofenceEvaluator geofenceEvaluator,
      DeliveryAttemptRecorder attemptRecorder,
      DeliveryProperties properties,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
    this.otpRepository = otpRepository;
    this.evidenciaEntregaRepository = evidenciaEntregaRepository;
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.uploadService = uploadService;
    this.geofenceEvaluator = geofenceEvaluator;
    this.attemptRecorder = attemptRecorder;
    this.properties = properties;
    this.events = events;
  }

  @Transactional
  public DeliveryCompletionResponse completar(
      UUID entregadorId, UUID pedidoId, DeliveryCompletionRequest request) {
    Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElseThrow(this::notFound);
    if (!pedido.estaAtribuidoA(entregadorId)) {
      throw new ForbiddenException(
          "NOT_ASSIGNED_COURIER", "Só o entregador atribuído finaliza esta entrega.");
    }
    // RF-26.12 — sem coleta confirmada pelo estabelecimento não há entrega a finalizar.
    if (pedido.getStatus() == OrderStatus.ACCEPTED) {
      throw new ConflictException(
          "ORDER_NOT_PICKED_UP", "O estabelecimento ainda não confirmou a coleta deste pedido.");
    }
    if (pedido.getStatus() != OrderStatus.PICKED_UP) {
      throw new ConflictException(
          "ORDER_ALREADY_FINALIZED", "Este pedido já foi finalizado ou não admite finalização.");
    }
    if (!geofenceEvaluator.dentroDoRaio(request.lat(), request.lng(), pedido)) {
      throw new BusinessRuleException(
          "OUTSIDE_GEOFENCE",
          "Você precisa estar no endereço de entrega para finalizar.",
          "RN-08.1");
    }

    return request.mode() == DeliveryCompletionRequest.Mode.CODE
        ? finalizarPorCodigo(entregadorId, pedido, request)
        : finalizarContestavel(entregadorId, pedido, request);
  }

  private DeliveryCompletionResponse finalizarPorCodigo(
      UUID entregadorId, Pedido pedido, DeliveryCompletionRequest request) {
    Otp otp =
        otpRepository
            .findByPedidoId(pedido.getId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Pedido aceito sem código de entrega — invariante quebrado (RF-13.4)."));

    if (otp.getStatus() == DeliveryCodeStatus.BLOCKED) {
      throw codigoInvalido(pedido.getId());
    }
    if (request.deliveryCode() == null
        || !DeliveryCodeService.hash(request.deliveryCode()).equals(otp.getCodigoHash())) {
      // RF-15.8: fora da transação principal — commit acontece mesmo que esta chamada lance em
      // seguida.
      attemptRecorder.registrarErro(otp.getId());
      throw codigoInvalido(pedido.getId());
    }

    Entregador entregador = entregadorRepository.findById(entregadorId).orElseThrow();
    boolean revisao = divergePosicaoReportada(entregador, request.lat(), request.lng());
    BigDecimal divergenciaMetros =
        entregador.getLat() == null
            ? null
            : BigDecimal.valueOf(
                geofenceEvaluator.distanciaEmMetros(
                    entregador.getLat(), entregador.getLongitude(), request.lat(), request.lng()));

    // RF-15.9 — tudo ou nada: pedido, evidência, código validado e lançamento na mesma transação.
    otp.validar();
    otpRepository.save(otp);
    pedido.finalizar();
    evidenciaEntregaRepository.save(
        new EvidenciaEntrega(
            UuidV7.next(),
            pedido.getId(),
            FinalizationType.CODE,
            null,
            request.lat(),
            request.lng(),
            revisao,
            divergenciaMetros));
    lancarFrete(pedido, entregadorId);
    entregador.incrementarEntregasRealizadas();

    events.publishEvent(new DeliveryFinalizedEvent(pedido.getId(), FinalizationType.CODE));

    return toResponse(pedido, FinalizationType.CODE);
  }

  private DeliveryCompletionResponse finalizarContestavel(
      UUID entregadorId, Pedido pedido, DeliveryCompletionRequest request) {
    // RF-17.1/RN-10.1 — liberado só pelo SERVIDOR (a escada de contingência, T-16).
    if (!pedido.isContestavelLiberado()) {
      throw new ForbiddenException(
          "CONTESTABLE_NOT_RELEASED",
          "O modo contestável só é liberado depois da escada de contingência se esgotar.");
    }
    if (request.proofUploadId() == null) {
      throw new BusinessRuleException(
          "MISSING_PROOF", "Foto obrigatória no modo contestável.", "RN-10.2");
    }
    // RF-17.2/critério 5 — mesma convenção de UploadService: upload de outro dono é 404, não 403,
    // para não revelar a existência do recurso a quem não é dono (ver
    // UploadService.validateForConsumption).
    Upload upload =
        uploadService.validateForConsumption(
            request.proofUploadId(), entregadorId, Purpose.DELIVERY_PROOF);
    if (evidenciaEntregaRepository.existsByUploadId(upload.getId())) {
      throw new ConflictException(
          "UPLOAD_ALREADY_LINKED", "Este upload já foi usado como evidência de outra entrega.");
    }

    Entregador entregador = entregadorRepository.findById(entregadorId).orElseThrow();
    boolean revisao = divergePosicaoReportada(entregador, request.lat(), request.lng());
    BigDecimal divergenciaMetros =
        entregador.getLat() == null
            ? null
            : BigDecimal.valueOf(
                geofenceEvaluator.distanciaEmMetros(
                    entregador.getLat(), entregador.getLongitude(), request.lat(), request.lng()));

    pedido.finalizarContestavel();
    evidenciaEntregaRepository.save(
        new EvidenciaEntrega(
            UuidV7.next(),
            pedido.getId(),
            FinalizationType.CONTESTABLE,
            upload.getId(),
            request.lat(),
            request.lng(),
            revisao,
            divergenciaMetros));
    lancarFrete(pedido, entregadorId);
    entregador.incrementarEntregasRealizadas();

    events.publishEvent(new DeliveryFinalizedEvent(pedido.getId(), FinalizationType.CONTESTABLE));

    return toResponse(pedido, FinalizationType.CONTESTABLE);
  }

  /**
   * RF-15.9/RF-17.5 — mesmo lançamento nos dois modos: a v1 não reserva, então não há o que
   * distinguir.
   */
  private void lancarFrete(Pedido pedido, UUID entregadorId) {
    if (lancamentoFreteRepository.existsByPedidoId(pedido.getId())) {
      return;
    }
    lancamentoFreteRepository.save(
        new LancamentoFrete(
            UuidV7.next(),
            pedido.getId(),
            entregadorId,
            pedido.getEstabelecimentoId(),
            pedido.getFreteFinal()));
  }

  /**
   * RF-15.7 — antifraude que MARCA, não bloqueia: falso positivo por GPS ruim não pode travar
   * entregador honesto na porta do cliente.
   */
  private boolean divergePosicaoReportada(Entregador entregador, BigDecimal lat, BigDecimal lng) {
    if (entregador.getLat() == null || entregador.getLongitude() == null) {
      return false;
    }
    double distanciaMetros =
        geofenceEvaluator.distanciaEmMetros(
            entregador.getLat(), entregador.getLongitude(), lat, lng);
    return distanciaMetros > properties.positionDivergenceToleranceMeters();
  }

  private DeliveryCompletionResponse toResponse(Pedido pedido, FinalizationType tipo) {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("order", LinkRef.get("/api/v1/orders/" + pedido.getId()));
    return new DeliveryCompletionResponse(
        pedido.getId(), pedido.getStatus(), tipo, pedido.getFinalizadoEm(), links);
  }

  private BusinessRuleException codigoInvalido(UUID pedidoId) {
    return new BusinessRuleException(
        "INVALID_DELIVERY_CODE",
        "Código incorreto. Se o problema persistir, acione a contingência em"
            + " /api/v1/orders/"
            + pedidoId
            + "/delivery/code-recoveries.",
        "RN-08.5");
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
