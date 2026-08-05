package com.uaiou.delivery.service;

import com.uaiou.delivery.ContingencyChannel;
import com.uaiou.delivery.ContingencyResult;
import com.uaiou.delivery.config.DeliveryProperties;
import com.uaiou.delivery.entity.ContingenciaOtp;
import com.uaiou.delivery.entity.PenalidadeEstabelecimento;
import com.uaiou.delivery.repository.ContingenciaOtpRepository;
import com.uaiou.delivery.repository.PenalidadeEstabelecimentoRepository;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.score.service.ScoreCalculationService;
import com.uaiou.shared.id.UuidV7;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-16.5 — ao vencer o prazo do degrau 2, avalia a atribuição da falha e libera o modo
 * contestável, <strong>em qualquer caso</strong>. RN-09.3: a atribuição é objetiva (telefone
 * ausente E sem repasse), não julgamento — é isso que permite penalidade automática sem arbitragem.
 *
 * <p>RN-09.5/RF-16.6: a penalidade entra na taxa do estabelecimento; nada aqui toca no entregador —
 * o mesmo evento alimenta dois indicadores com donos diferentes, e só um deles é punido.
 */
@Component
public class ContingencyExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(ContingencyExpiryJob.class);
  private static final int DEGRAU_ESTABELECIMENTO = 2;

  private final ContingenciaOtpRepository contingenciaOtpRepository;
  private final PedidoRepository pedidoRepository;
  private final PenalidadeEstabelecimentoRepository penalidadeRepository;
  private final DeliveryProperties properties;
  private final ScoreCalculationService scoreCalculationService;

  public ContingencyExpiryJob(
      ContingenciaOtpRepository contingenciaOtpRepository,
      PedidoRepository pedidoRepository,
      PenalidadeEstabelecimentoRepository penalidadeRepository,
      DeliveryProperties properties,
      ScoreCalculationService scoreCalculationService) {
    this.contingenciaOtpRepository = contingenciaOtpRepository;
    this.pedidoRepository = pedidoRepository;
    this.penalidadeRepository = penalidadeRepository;
    this.properties = properties;
    this.scoreCalculationService = scoreCalculationService;
  }

  @Scheduled(fixedDelayString = "PT1M")
  @Transactional
  public void resolverPrazosVencidos() {
    List<ContingenciaOtp> vencidos =
        contingenciaOtpRepository.findByDegrauAndResultadoAndPrazoEmBefore(
            DEGRAU_ESTABELECIMENTO, ContingencyResult.NOTIFIED, Instant.now());

    for (ContingenciaOtp notificacao : vencidos) {
      resolverUm(notificacao);
    }
    if (!vencidos.isEmpty()) {
      log.info("Contingência: {} prazo(s) de degrau 2 resolvido(s).", vencidos.size());
    }
  }

  private void resolverUm(ContingenciaOtp notificacao) {
    // Idempotência: uma vez liberado, o pedido nunca cria novo degrau 2 (ContingencyService checa
    // isso antes de escalar) — então "já liberado" é garantia suficiente de que este exato episódio
    // já foi resolvido, sem precisar comparar qual linha é "a mais recente". Um repasse (RF-16.4)
    // não fecha esta obrigação: ele só afasta a penalidade (critério 7) — a liberação em si
    // acontece
    // sempre que o prazo vence, "em qualquer caso" (RF-16.5).
    Pedido pedido = pedidoRepository.findById(notificacao.getPedidoId()).orElse(null);
    if (pedido == null || pedido.isContestavelLiberado()) {
      return;
    }

    boolean semTelefone = pedido.getRecebedorTelefone() == null;
    boolean semRepasse =
        contingenciaOtpRepository.findByPedidoIdOrderByCriadoEmAsc(pedido.getId()).stream()
            .noneMatch(
                evento ->
                    evento.getCanal() == ContingencyChannel.MERCHANT
                        && evento.getResultado() == ContingencyResult.DISPATCHED);

    if (semTelefone && semRepasse) {
      penalidadeRepository.save(
          new PenalidadeEstabelecimento(
              UuidV7.next(),
              pedido.getEstabelecimentoId(),
              pedido.getId(),
              "Contingência de código não resolvida: sem telefone do recebedor e sem repasse do"
                  + " estabelecimento dentro do prazo.",
              properties.penaltyPoints()));
    }

    contingenciaOtpRepository.save(
        new ContingenciaOtp(
            UuidV7.next(),
            pedido.getId(),
            DEGRAU_ESTABELECIMENTO,
            ContingencyChannel.MERCHANT,
            ContingencyResult.EXPIRED,
            null));
    pedido.liberarContestavel();

    // RF-20.1/RF-20.3 — a taxa de contingência do estabelecimento só muda aqui: é onde o insumo
    // (penalidade aplicada ou não) nasce.
    scoreCalculationService.recalcularEstabelecimento(pedido.getEstabelecimentoId());
  }
}
