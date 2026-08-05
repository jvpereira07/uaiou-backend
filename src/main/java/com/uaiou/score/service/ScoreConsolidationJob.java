package com.uaiou.score.service;

import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-20.3 — "consolidação periódica em lote" além do recálculo por evento: rede de segurança contra
 * um evento perdido (ex.: uma penalidade aplicada por uma corrida rara sem o listener disparar).
 * Roda sobre a base inteira porque a v1 não tem volume que justifique um recorte incremental —
 * otimizar isso antes de medir seria complexidade paga sem necessidade comprovada.
 */
@Component
public class ScoreConsolidationJob {

  private static final Logger log = LoggerFactory.getLogger(ScoreConsolidationJob.class);

  private final EntregadorRepository entregadorRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final ScoreCalculationService scoreCalculationService;

  public ScoreConsolidationJob(
      EntregadorRepository entregadorRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      ScoreCalculationService scoreCalculationService) {
    this.entregadorRepository = entregadorRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.scoreCalculationService = scoreCalculationService;
  }

  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void consolidar() {
    int entregadores = 0;
    for (var entregador : entregadorRepository.findAll()) {
      scoreCalculationService.recalcularEntregador(entregador.getUsuarioId());
      entregadores++;
    }
    int estabelecimentos = 0;
    for (var estabelecimento : estabelecimentoRepository.findAll()) {
      scoreCalculationService.recalcularEstabelecimento(estabelecimento.getUsuarioId());
      estabelecimentos++;
    }
    log.info(
        "Consolidação de score: {} entregador(es), {} estabelecimento(s).",
        entregadores,
        estabelecimentos);
  }
}
