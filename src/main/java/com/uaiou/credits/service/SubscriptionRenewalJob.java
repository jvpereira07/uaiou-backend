package com.uaiou.credits.service;

import com.uaiou.credits.SubscriptionStatus;
import com.uaiou.credits.entity.Assinatura;
import com.uaiou.credits.entity.Plano;
import com.uaiou.credits.repository.AssinaturaRepository;
import com.uaiou.credits.repository.PlanoRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-09.4: na virada do ciclo, credita a cota do plano ativo e avança {@code proximaRenovacao}.
 * Idempotente por construção, não por chave de idempotência: {@link Assinatura#avancarCiclo()} roda
 * na mesma transação que credita a cota, então a query de "vencidas" (RF-09.4, critério de aceite
 * 3) nunca mais encontra a mesma assinatura depois de processada — não importa quantas vezes o job
 * rode no mesmo período.
 */
@Component
public class SubscriptionRenewalJob {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalJob.class);

  private final AssinaturaRepository assinaturaRepository;
  private final PlanoRepository planoRepository;
  private final CreditWalletService creditWalletService;

  public SubscriptionRenewalJob(
      AssinaturaRepository assinaturaRepository,
      PlanoRepository planoRepository,
      CreditWalletService creditWalletService) {
    this.assinaturaRepository = assinaturaRepository;
    this.planoRepository = planoRepository;
    this.creditWalletService = creditWalletService;
  }

  @Scheduled(fixedDelayString = "PT1H")
  @Transactional
  public void renovarCiclosVencidos() {
    List<Assinatura> vencidas =
        assinaturaRepository.findByStatusAndProximaRenovacaoLessThanEqual(
            SubscriptionStatus.ACTIVE, LocalDate.now());
    for (Assinatura assinatura : vencidas) {
      Plano plano =
          planoRepository
              .findById(assinatura.getPlanoId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Assinatura " + assinatura.getId() + " referencia plano inexistente."));
      assinatura.avancarCiclo();
      assinaturaRepository.save(assinatura);
      creditWalletService.creditarCota(
          assinatura.getEstabelecimentoId(), plano.getCotaMensalCreditos(), assinatura.getId());
    }
    if (!vencidas.isEmpty()) {
      log.info("Renovação de ciclo: {} assinatura(s) renovada(s).", vencidas.size());
    }
  }
}
