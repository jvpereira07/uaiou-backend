package com.uaiou.orders.service;

import com.uaiou.orders.config.PickupProperties;
import com.uaiou.orders.entity.DesistenciaPedido;
import com.uaiou.orders.repository.DesistenciaPedidoRepository;
import com.uaiou.shared.error.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * RF-26.29 — limite de desistências. Separado do serviço de desistência porque quem o aplica é o
 * aceite ({@link CourierEligibilityGuard}), e o aceite não deve depender da transição inversa.
 *
 * <p>Ao atingir o máximo na janela de 24 h, o bloqueio dura {@code withdrawalCooldown} a partir da
 * desistência que completou a conta. Passado o bloqueio, o entregador volta a aceitar — e a próxima
 * desistência que conta, ainda dentro da janela, bloqueia de novo.
 */
@Component
public class WithdrawalLimitPolicy {

  private static final Duration JANELA = Duration.ofHours(24);

  private final DesistenciaPedidoRepository desistenciaRepository;
  private final PickupProperties properties;

  public WithdrawalLimitPolicy(
      DesistenciaPedidoRepository desistenciaRepository, PickupProperties properties) {
    this.desistenciaRepository = desistenciaRepository;
    this.properties = properties;
  }

  public void exigirForaDoBloqueio(UUID entregadorId) {
    Instant agora = Instant.now();
    List<DesistenciaPedido> recentes =
        desistenciaRepository
            .findByEntregadorIdAndContaPenalidadeTrueAndCriadoEmAfterOrderByCriadoEmDesc(
                entregadorId, agora.minus(JANELA));
    if (recentes.size() < properties.withdrawalMaxPer24h()) {
      return;
    }
    Instant liberadoEm = recentes.getFirst().getCriadoEm().plus(properties.withdrawalCooldown());
    if (agora.isBefore(liberadoEm)) {
      throw new BusinessRuleException(
          "WITHDRAWAL_LIMIT_REACHED",
          "Você desistiu de muitas entregas nas últimas 24 horas e está temporariamente sem poder"
              + " aceitar pedidos.",
          "RF-26.29",
          Map.of("blockedUntil", liberadoEm.toString()));
    }
  }
}
