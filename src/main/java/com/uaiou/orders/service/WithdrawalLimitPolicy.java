package com.uaiou.orders.service;

import com.uaiou.orders.entity.DesistenciaPedido;
import com.uaiou.orders.limits.BehaviorLimitPolicy;
import com.uaiou.orders.limits.BehaviorLimitRule;
import com.uaiou.orders.repository.DesistenciaPedidoRepository;
import com.uaiou.shared.error.BusinessRuleException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * RF-26.29 — limite de desistências. Separado do serviço de desistência porque quem o aplica é o
 * aceite ({@link CourierEligibilityGuard}), e o aceite não deve depender da transição inversa.
 *
 * <p>Máximo, janela e bloqueio são calibrados pelo admin ({@link
 * BehaviorLimitRule#COURIER_WITHDRAWALS}); só desistências que contam penalidade entram na conta.
 */
@Component
public class WithdrawalLimitPolicy {

  private final DesistenciaPedidoRepository desistenciaRepository;
  private final BehaviorLimitPolicy behaviorLimitPolicy;

  public WithdrawalLimitPolicy(
      DesistenciaPedidoRepository desistenciaRepository, BehaviorLimitPolicy behaviorLimitPolicy) {
    this.desistenciaRepository = desistenciaRepository;
    this.behaviorLimitPolicy = behaviorLimitPolicy;
  }

  public void exigirForaDoBloqueio(UUID entregadorId) {
    behaviorLimitPolicy
        .bloqueadoAte(
            BehaviorLimitRule.COURIER_WITHDRAWALS,
            desde ->
                desistenciaRepository
                    .findByEntregadorIdAndContaPenalidadeTrueAndCriadoEmAfterOrderByCriadoEmDesc(
                        entregadorId, desde)
                    .stream()
                    .map(DesistenciaPedido::getCriadoEm)
                    .toList())
        .ifPresent(
            liberadoEm -> {
              throw new BusinessRuleException(
                  "WITHDRAWAL_LIMIT_REACHED",
                  "Você desistiu de muitas entregas recentemente e está temporariamente sem poder"
                      + " aceitar pedidos.",
                  "RF-26.29",
                  Map.of("blockedUntil", liberadoEm.toString()));
            });
  }
}
