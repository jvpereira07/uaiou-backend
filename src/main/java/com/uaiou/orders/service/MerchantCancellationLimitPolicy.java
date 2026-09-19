package com.uaiou.orders.service;

import com.uaiou.orders.limits.BehaviorLimitPolicy;
import com.uaiou.orders.limits.BehaviorLimitRule;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.BusinessRuleException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Espelho do limite de desistências para o outro lado: a loja que cancela demais fica sem publicar
 * por um tempo. Só contam cancelamentos da própria loja — os feitos pela plataforma (admin ou
 * timeout) não são comportamento dela.
 */
@Component
public class MerchantCancellationLimitPolicy {

  private static final List<String> MOTIVOS_DA_PLATAFORMA =
      List.of(PlatformInterventionService.MOTIVO_ADMIN, PlatformInterventionService.MOTIVO_TIMEOUT);

  private final PedidoRepository pedidoRepository;
  private final BehaviorLimitPolicy behaviorLimitPolicy;

  public MerchantCancellationLimitPolicy(
      PedidoRepository pedidoRepository, BehaviorLimitPolicy behaviorLimitPolicy) {
    this.pedidoRepository = pedidoRepository;
    this.behaviorLimitPolicy = behaviorLimitPolicy;
  }

  public void exigirForaDoBloqueio(UUID estabelecimentoId) {
    behaviorLimitPolicy
        .bloqueadoAte(
            BehaviorLimitRule.MERCHANT_CANCELLATIONS,
            desde ->
                pedidoRepository.cancelamentosDoEstabelecimentoDesde(
                    estabelecimentoId, desde, MOTIVOS_DA_PLATAFORMA))
        .ifPresent(
            liberadoEm -> {
              throw new BusinessRuleException(
                  "CANCELLATION_LIMIT_REACHED",
                  "Você cancelou muitos pedidos recentemente e está temporariamente sem poder"
                      + " publicar novos pedidos.",
                  "LIMITE-CANCELAMENTO",
                  Map.of("blockedUntil", liberadoEm.toString()));
            });
  }
}
