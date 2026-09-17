package com.uaiou.orders.service;

import com.uaiou.delivery.ContingencyChannel;
import com.uaiou.delivery.ContingencyResult;
import com.uaiou.delivery.config.DeliveryProperties;
import com.uaiou.delivery.entity.ContingenciaOtp;
import com.uaiou.delivery.entity.Otp;
import com.uaiou.delivery.repository.ContingenciaOtpRepository;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.delivery.service.DeliveryCodeCipher;
import com.uaiou.delivery.service.SmsSender;
import com.uaiou.orders.ContingencyEscalatedEvent;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.dto.CodeDispatchRequest;
import com.uaiou.orders.dto.CodeDispatchResponse;
import com.uaiou.orders.dto.CodeRecoveryRequest;
import com.uaiou.orders.dto.CodeRecoveryResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-16.1 a RF-16.4 — a escada de contingência: SMS (degrau 1) → estabelecimento (degrau 2).
 * RF-16.5 (job de expiração e atribuição de falha) é {@link ContingencyExpiryJob}.
 */
@Service
public class ContingencyService {

  private final PedidoRepository pedidoRepository;
  private final OtpRepository otpRepository;
  private final ContingenciaOtpRepository contingenciaOtpRepository;
  private final DeliveryCodeCipher cipher;
  private final SmsSender smsSender;
  private final DeliveryProperties properties;
  private final ApplicationEventPublisher events;

  public ContingencyService(
      PedidoRepository pedidoRepository,
      OtpRepository otpRepository,
      ContingenciaOtpRepository contingenciaOtpRepository,
      DeliveryCodeCipher cipher,
      SmsSender smsSender,
      DeliveryProperties properties,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.otpRepository = otpRepository;
    this.contingenciaOtpRepository = contingenciaOtpRepository;
    this.cipher = cipher;
    this.smsSender = smsSender;
    this.properties = properties;
    this.events = events;
  }

  @Transactional
  public CodeRecoveryResponse acionar(
      UUID entregadorId, UUID pedidoId, CodeRecoveryRequest request) {
    Pedido pedido = requireAtribuido(entregadorId, pedidoId);
    // RF-26.12 — a contingência do código só existe depois da coleta.
    if (pedido.getStatus() == OrderStatus.ACCEPTED) {
      throw new ConflictException(
          "ORDER_NOT_PICKED_UP", "O estabelecimento ainda não confirmou a coleta deste pedido.");
    }
    if (pedido.getStatus() != OrderStatus.PICKED_UP) {
      throw new ConflictException(
          "ORDER_ALREADY_FINALIZED", "Este pedido já foi finalizado ou não admite contingência.");
    }
    Otp otp = otpRepository.findByPedidoId(pedidoId).orElseThrow(this::notFound);

    // RF-16.7 — reentrância: degrau 2 já aberto devolve o estado atual, sem reiniciar o prazo.
    Optional<ContingenciaOtp> degrau2Aberto = degrauDoisAberto(pedidoId);
    if (degrau2Aberto.isPresent()) {
      return toRecoveryResponse(
          pedidoId, 2, pedido.isContestavelLiberado(), degrau2Aberto.get().getPrazoEm());
    }
    if (pedido.isContestavelLiberado()) {
      // A escada já terminou (job já rodou); nada novo a acionar.
      return toRecoveryResponse(pedidoId, 2, true, null);
    }

    int reenviosFeitos =
        contingenciaOtpRepository.countByPedidoIdAndCanalAndResultado(
            pedidoId, ContingencyChannel.SMS, ContingencyResult.RESENT);
    boolean podeReenviarSms =
        pedido.getRecebedorTelefone() != null && reenviosFeitos < properties.smsResendLimit();

    if (podeReenviarSms) {
      String codigo = cipher.decifrar(otp.getCodigoCifrado());
      smsSender.send(
          pedido.getRecebedorTelefone(),
          "Seu código de entrega uaiou é " + codigo + ". Informe ao entregador na porta.");
      contingenciaOtpRepository.save(
          new ContingenciaOtp(
              UuidV7.next(), pedidoId, 1, ContingencyChannel.SMS, ContingencyResult.RESENT, null));
      return toRecoveryResponse(pedidoId, 1, false, null);
    }

    // RF-16.3 — degrau 2: grava o prazo e trava o fluxo do entregador até ele vencer.
    Instant prazo =
        Instant.now().plus(properties.contingencyDeadline()).truncatedTo(ChronoUnit.MICROS);
    contingenciaOtpRepository.save(
        new ContingenciaOtp(
            UuidV7.next(),
            pedidoId,
            2,
            ContingencyChannel.MERCHANT,
            ContingencyResult.NOTIFIED,
            prazo));
    events.publishEvent(new ContingencyEscalatedEvent(pedidoId, prazo));

    return toRecoveryResponse(pedidoId, 2, false, prazo);
  }

  /**
   * RF-16.4 — registra o ato de repasse (a marca que o job usa para não culpar quem repassou por
   * fora do app) e, com telefone no corpo, corrige o pedido e recupera o degrau 1 para uma entrega
   * que nasceu sem telefone.
   */
  @Transactional
  public CodeDispatchResponse despachar(
      UUID estabelecimentoId, UUID pedidoId, CodeDispatchRequest request) {
    Pedido pedido =
        pedidoRepository
            .findByIdForUpdate(pedidoId)
            .filter(candidato -> candidato.pertenceAoEstabelecimento(estabelecimentoId))
            .orElseThrow(this::notFound);
    Otp otp = otpRepository.findByPedidoId(pedidoId).orElseThrow(this::notFound);

    if (request.receiverPhone() != null && !request.receiverPhone().isBlank()) {
      pedido.atualizarTelefoneRecebedor(request.receiverPhone());
      String codigo = cipher.decifrar(otp.getCodigoCifrado());
      smsSender.send(
          request.receiverPhone(),
          "Seu código de entrega uaiou é " + codigo + ". Informe ao entregador na porta.");
    }

    Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    contingenciaOtpRepository.save(
        new ContingenciaOtp(
            UuidV7.next(),
            pedidoId,
            2,
            ContingencyChannel.MERCHANT,
            ContingencyResult.DISPATCHED,
            null));

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("delivery", LinkRef.get("/api/v1/orders/" + pedidoId + "/delivery"));
    return new CodeDispatchResponse(pedidoId, agora, links);
  }

  private Optional<ContingenciaOtp> degrauDoisAberto(UUID pedidoId) {
    return contingenciaOtpRepository
        .findFirstByPedidoIdAndDegrauOrderByCriadoEmDesc(pedidoId, 2)
        .filter(
            ultimo ->
                ultimo.getResultado() == ContingencyResult.NOTIFIED
                    && ultimo.getPrazoEm().isAfter(Instant.now()));
  }

  private CodeRecoveryResponse toRecoveryResponse(
      UUID pedidoId, int step, boolean contestableReleased, Instant deadline) {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("delivery", LinkRef.get("/api/v1/orders/" + pedidoId + "/delivery"));
    return new CodeRecoveryResponse(step, contestableReleased, deadline, links);
  }

  private Pedido requireAtribuido(UUID entregadorId, UUID pedidoId) {
    Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(this::notFound);
    if (!pedido.estaAtribuidoA(entregadorId)) {
      throw new ForbiddenException(
          "NOT_ASSIGNED_COURIER", "Só o entregador atribuído aciona a contingência.");
    }
    return pedido;
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
