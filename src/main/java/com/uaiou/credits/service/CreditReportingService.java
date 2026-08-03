package com.uaiou.credits.service;

import com.uaiou.credits.CreditTransactionType;
import com.uaiou.credits.dto.SubscriptionSummary;
import com.uaiou.credits.entity.Assinatura;
import com.uaiou.credits.entity.Plano;
import com.uaiou.credits.repository.TransacaoCreditoRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-09.8 — {@code consumedThisCycle}, reusado tanto por {@code GET /me/credits} (o próprio
 * estabelecimento) quanto por {@code PUT /admin/merchants/{id}/plan} (o admin vê o mesmo número na
 * resposta da troca). Sem coluna própria de "início do ciclo vigente": deriva-se de {@code
 * proximaRenovacao - 1 mês}, que vale tanto para o primeiro ciclo (nunca renovado) quanto depois de
 * N renovações — {@link Assinatura#avancarCiclo()} sempre soma 1 mês, então subtrair 1 sempre volta
 * exatamente ao início do ciclo corrente.
 */
@Service
public class CreditReportingService {

  private final TransacaoCreditoRepository transacaoCreditoRepository;

  public CreditReportingService(TransacaoCreditoRepository transacaoCreditoRepository) {
    this.transacaoCreditoRepository = transacaoCreditoRepository;
  }

  @Transactional(readOnly = true)
  public SubscriptionSummary buildSummary(
      UUID estabelecimentoId, Assinatura assinatura, Plano plano) {
    Instant cycleStart =
        assinatura.getProximaRenovacao().minusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    int consumed =
        transacaoCreditoRepository.somarQuantidadeDesde(
            estabelecimentoId, CreditTransactionType.POSTING_CONSUMPTION, cycleStart);
    return new SubscriptionSummary(
        plano.getNome(),
        plano.getCotaMensalCreditos(),
        consumed,
        assinatura.getProximaRenovacao(),
        assinatura.getStatus());
  }
}
