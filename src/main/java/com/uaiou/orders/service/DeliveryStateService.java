package com.uaiou.orders.service;

import com.uaiou.delivery.ContingencyResult;
import com.uaiou.delivery.config.DeliveryProperties;
import com.uaiou.delivery.entity.ContingenciaOtp;
import com.uaiou.delivery.entity.Otp;
import com.uaiou.delivery.repository.ContingenciaOtpRepository;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.delivery.service.GeofenceEvaluator;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.dto.DeliveryStateResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code GET /orders/{id}/delivery} — RF-15.3, a rota mais quente do serviço (polling). */
@Service
public class DeliveryStateService {

  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;
  private final OtpRepository otpRepository;
  private final ContingenciaOtpRepository contingenciaOtpRepository;
  private final GeofenceEvaluator geofenceEvaluator;
  private final DeliveryProperties properties;

  public DeliveryStateService(
      PedidoRepository pedidoRepository,
      EntregadorRepository entregadorRepository,
      OtpRepository otpRepository,
      ContingenciaOtpRepository contingenciaOtpRepository,
      GeofenceEvaluator geofenceEvaluator,
      DeliveryProperties properties) {
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
    this.otpRepository = otpRepository;
    this.contingenciaOtpRepository = contingenciaOtpRepository;
    this.geofenceEvaluator = geofenceEvaluator;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public DeliveryStateResponse get(UUID entregadorId, UUID pedidoId) {
    Pedido pedido = requireAtribuido(entregadorId, pedidoId);
    Entregador entregador = entregadorRepository.findById(entregadorId).orElseThrow(this::notFound);

    GeofenceEvaluator.Resultado geofence = geofenceEvaluator.avaliar(entregador, pedido);
    Otp otp = otpRepository.findByPedidoId(pedidoId).orElse(null);

    Integer degrauAberto = degrauContingenciaAberto(pedidoId);
    DeliveryStateResponse.Contingency contingency =
        new DeliveryStateResponse.Contingency(
            degrauAberto,
            pedido.isContestavelLiberado(),
            prazoDoDegrauAberto(pedidoId, degrauAberto));

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/orders/" + pedidoId + "/delivery"));
    links.put("order", LinkRef.get("/api/v1/orders/" + pedidoId));
    // RF-26.12 — finalizar e acionar contingência só depois da coleta.
    if (geofence.inside() && pedido.getStatus() == OrderStatus.PICKED_UP) {
      links.put("completion", LinkRef.get("/api/v1/orders/" + pedidoId + "/delivery/completion"));
      links.put(
          "codeRecoveries",
          LinkRef.get("/api/v1/orders/" + pedidoId + "/delivery/code-recoveries"));
    }
    if (pedido.isContestavelLiberado() && pedido.getStatus() == OrderStatus.PICKED_UP) {
      links.put("completion", LinkRef.get("/api/v1/orders/" + pedidoId + "/delivery/completion"));
    }

    return new DeliveryStateResponse(
        pedidoId,
        pedido.getStatus(),
        new DeliveryStateResponse.Geofence(
            geofence.inside(),
            geofence.radiusMeters(),
            geofence.distanceMeters(),
            geofence.reason()),
        otp == null
            ? null
            : new DeliveryStateResponse.DeliveryCode(
                otp.getStatus(),
                Math.max(0, properties.maxAttempts() - otp.getTentativas()),
                List.of("sms", "merchant")),
        contingency,
        links);
  }

  /** RF-16.5/pacote com o job: {@code null} quando não há degrau 2 esperando resolução. */
  private Integer degrauContingenciaAberto(UUID pedidoId) {
    return contingenciaOtpRepository
        .findFirstByPedidoIdAndDegrauOrderByCriadoEmDesc(pedidoId, 2)
        .filter(ultimo -> ultimo.getResultado() == ContingencyResult.NOTIFIED)
        .map(ContingenciaOtp::getDegrau)
        .orElse(null);
  }

  private java.time.Instant prazoDoDegrauAberto(UUID pedidoId, Integer degrauAberto) {
    if (degrauAberto == null) {
      return null;
    }
    return contingenciaOtpRepository
        .findFirstByPedidoIdAndDegrauOrderByCriadoEmDesc(pedidoId, 2)
        .map(ContingenciaOtp::getPrazoEm)
        .orElse(null);
  }

  private Pedido requireAtribuido(UUID entregadorId, UUID pedidoId) {
    Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(this::notFound);
    if (!pedido.estaAtribuidoA(entregadorId)) {
      throw new ForbiddenException(
          "NOT_ASSIGNED_COURIER", "Só o entregador atribuído acompanha esta entrega.");
    }
    return pedido;
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
