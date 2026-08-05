package com.uaiou.stats.service;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.counteroffers.entity.Contraoferta;
import com.uaiou.counteroffers.repository.ContraofertaRepository;
import com.uaiou.credits.CreditTransactionType;
import com.uaiou.credits.SubscriptionStatus;
import com.uaiou.credits.entity.Assinatura;
import com.uaiou.credits.entity.Plano;
import com.uaiou.credits.repository.AssinaturaRepository;
import com.uaiou.credits.repository.PlanoRepository;
import com.uaiou.credits.repository.TransacaoCreditoRepository;
import com.uaiou.delivery.LedgerStatus;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.delivery.repository.PenalidadeEstabelecimentoRepository;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.money.Money;
import com.uaiou.stats.dto.CourierStatsResponse;
import com.uaiou.stats.dto.MerchantStatsResponse;
import com.uaiou.stats.dto.StatsPeriod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-22.1 a RF-22.5 — painéis individuais. Escopo travado no token (RF-22.1): todo método recebe o
 * id de quem chamou, nunca um parâmetro de usuário — não existe caminho para consultar desempenho
 * alheio.
 */
@Service
public class StatsService {

  private static final List<OrderStatus> FINALIZADOS =
      List.of(OrderStatus.FINALIZED, OrderStatus.CONTESTABLE_FINALIZED);

  private final PedidoRepository pedidoRepository;
  private final ContraofertaRepository contraofertaRepository;
  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final PenalidadeEstabelecimentoRepository penalidadeRepository;
  private final AssinaturaRepository assinaturaRepository;
  private final PlanoRepository planoRepository;
  private final TransacaoCreditoRepository transacaoCreditoRepository;

  public StatsService(
      PedidoRepository pedidoRepository,
      ContraofertaRepository contraofertaRepository,
      LancamentoFreteRepository lancamentoFreteRepository,
      PenalidadeEstabelecimentoRepository penalidadeRepository,
      AssinaturaRepository assinaturaRepository,
      PlanoRepository planoRepository,
      TransacaoCreditoRepository transacaoCreditoRepository) {
    this.pedidoRepository = pedidoRepository;
    this.contraofertaRepository = contraofertaRepository;
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.penalidadeRepository = penalidadeRepository;
    this.assinaturaRepository = assinaturaRepository;
    this.planoRepository = planoRepository;
    this.transacaoCreditoRepository = transacaoCreditoRepository;
  }

  @Transactional(readOnly = true)
  public CourierStatsResponse courierStats(UUID entregadorId, String periodParam) {
    StatsPeriod period = resolvePeriod(periodParam, null);

    List<Pedido> pedidos =
        pedidoRepository.findByEntregadorIdAndCriadoEmBetween(
            entregadorId, period.from(), period.to());
    List<Pedido> finalizados =
        pedidos.stream().filter(p -> FINALIZADOS.contains(p.getStatus())).toList();

    List<LancamentoFrete> lancamentos =
        lancamentoFreteRepository.findByEntregadorIdAndCriadoEmBetween(
            entregadorId, period.from(), period.to());
    Money receivable =
        somar(lancamentos.stream().filter(l -> l.getStatus() == LedgerStatus.RECEIVABLE).toList());
    Money settled =
        somar(lancamentos.stream().filter(l -> l.getStatus() == LedgerStatus.SETTLED).toList());
    Money averageTicket =
        lancamentos.isEmpty()
            ? null
            : Money.of(
                somar(lancamentos)
                    .amount()
                    .divide(BigDecimal.valueOf(lancamentos.size()), 2, RoundingMode.HALF_UP));

    List<Contraoferta> propostas =
        contraofertaRepository.findByEntregadorIdAndCriadoEmBetween(
            entregadorId, period.from(), period.to());
    BigDecimal counterofferSuccessRate =
        taxa(
            propostas.stream().filter(c -> c.getStatus() == CounterofferStatus.ACCEPTED).count(),
            propostas.size());

    Double averageDeliveryMinutes = mediaMinutos(finalizados);
    BigDecimal cleanFinalizationRate =
        taxa(
            finalizados.stream().filter(p -> p.getStatus() == OrderStatus.FINALIZED).count(),
            finalizados.size());

    return new CourierStatsResponse(
        period,
        finalizados.size(),
        receivable,
        settled,
        averageTicket,
        counterofferSuccessRate,
        averageDeliveryMinutes,
        cleanFinalizationRate,
        null,
        null);
  }

  @Transactional(readOnly = true)
  public MerchantStatsResponse merchantStats(UUID estabelecimentoId, String periodParam) {
    StatsPeriod period = resolvePeriod(periodParam, estabelecimentoId);

    List<Pedido> pedidos =
        pedidoRepository.findByEstabelecimentoIdAndCriadoEmBetween(
            estabelecimentoId, period.from(), period.to());
    List<Pedido> publicados =
        pedidos.stream().filter(p -> p.getStatus() != OrderStatus.CREATED).toList();
    List<Pedido> concluidos =
        pedidos.stream().filter(p -> FINALIZADOS.contains(p.getStatus())).toList();
    List<Pedido> atribuidos = pedidos.stream().filter(p -> p.getAceitoEm() != null).toList();

    BigDecimal matchRate = taxa(concluidos.size(), publicados.size());
    Double medianTimeToAssignment = medianaMinutosAteAtribuicao(atribuidos);

    List<Contraoferta> recebidas =
        contraofertaRepository.findRecebidasPeloEstabelecimento(
            estabelecimentoId, period.from(), period.to());
    BigDecimal counterofferAcceptRate =
        taxa(
            recebidas.stream().filter(c -> c.getStatus() == CounterofferStatus.ACCEPTED).count(),
            recebidas.size());

    Money spread = spread(atribuidos);
    Money freightSpend = somarFreteFinal(concluidos);

    Assinatura assinatura =
        assinaturaRepository
            .findByEstabelecimentoIdAndStatus(estabelecimentoId, SubscriptionStatus.ACTIVE)
            .orElse(null);
    Integer quota =
        assinatura == null
            ? null
            : planoRepository
                .findById(assinatura.getPlanoId())
                .map(Plano::getCotaMensalCreditos)
                .orElse(null);
    int consumido =
        transacaoCreditoRepository.somarQuantidadeDesde(
            estabelecimentoId, CreditTransactionType.POSTING_CONSUMPTION, period.from());

    int penalidades =
        penalidadeRepository.countByEstabelecimentoIdAndCriadoEmBetween(
            estabelecimentoId, period.from(), period.to());
    BigDecimal codeContingencyRate = taxa(penalidades, concluidos.size());

    return new MerchantStatsResponse(
        period,
        publicados.size(),
        concluidos.size(),
        matchRate,
        medianTimeToAssignment,
        counterofferAcceptRate,
        spread,
        freightSpend,
        consumido,
        quota,
        codeContingencyRate);
  }

  private StatsPeriod resolvePeriod(String periodParam, UUID estabelecimentoId) {
    Instant agora = Instant.now();
    return switch (periodParam == null ? "30d" : periodParam) {
      case "7d" -> new StatsPeriod("7d", agora.minus(Duration.ofDays(7)), agora);
      case "30d" -> new StatsPeriod("30d", agora.minus(Duration.ofDays(30)), agora);
      case "cycle" -> cyclePeriod(estabelecimentoId, agora);
      default ->
          throw new BadRequestException(
              "UNSUPPORTED_PERIOD", "\"period\" deve ser \"7d\", \"30d\" ou \"cycle\".");
    };
  }

  /** RF-22.1 — "cycle" só existe de fato para o estabelecimento (ciclo de assinatura, T-09). */
  private StatsPeriod cyclePeriod(UUID estabelecimentoId, Instant agora) {
    if (estabelecimentoId == null) {
      return new StatsPeriod("cycle", agora.minus(Duration.ofDays(30)), agora);
    }
    return assinaturaRepository
        .findByEstabelecimentoIdAndStatus(estabelecimentoId, SubscriptionStatus.ACTIVE)
        .map(
            assinatura ->
                new StatsPeriod(
                    "cycle",
                    assinatura.getInicio().atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                    agora))
        .orElse(new StatsPeriod("cycle", agora.minus(Duration.ofDays(30)), agora));
  }

  private BigDecimal taxa(long numerador, long denominador) {
    if (denominador == 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(numerador)
        .divide(BigDecimal.valueOf(denominador), 4, RoundingMode.HALF_UP);
  }

  private Money somar(List<LancamentoFrete> lancamentos) {
    return lancamentos.stream().map(LancamentoFrete::getValor).reduce(Money.ZERO, Money::add);
  }

  private Money somarFreteFinal(List<Pedido> pedidos) {
    Money total = Money.ZERO;
    for (Pedido pedido : pedidos) {
      if (pedido.getFreteFinal() != null) {
        total = total.add(pedido.getFreteFinal());
      }
    }
    return total;
  }

  private Money spread(List<Pedido> atribuidos) {
    List<Pedido> comValor =
        atribuidos.stream()
            .filter(p -> p.getFreteFinal() != null && p.getFreteProposto() != null)
            .toList();
    if (comValor.isEmpty()) {
      return Money.ZERO;
    }
    BigDecimal soma = BigDecimal.ZERO;
    for (Pedido pedido : comValor) {
      soma = soma.add(pedido.getFreteFinal().amount().subtract(pedido.getFreteProposto().amount()));
    }
    return Money.of(soma.divide(BigDecimal.valueOf(comValor.size()), 2, RoundingMode.HALF_UP));
  }

  private Double mediaMinutos(List<Pedido> finalizados) {
    List<Pedido> comTempos =
        finalizados.stream()
            .filter(p -> p.getAceitoEm() != null && p.getFinalizadoEm() != null)
            .toList();
    if (comTempos.isEmpty()) {
      return null;
    }
    double somaMinutos =
        comTempos.stream()
            .mapToDouble(
                p -> Duration.between(p.getAceitoEm(), p.getFinalizadoEm()).toSeconds() / 60.0)
            .sum();
    return somaMinutos / comTempos.size();
  }

  private Double medianaMinutosAteAtribuicao(List<Pedido> atribuidos) {
    List<Double> minutos =
        atribuidos.stream()
            .map(p -> Duration.between(p.getCriadoEm(), p.getAceitoEm()).toSeconds() / 60.0)
            .sorted()
            .toList();
    if (minutos.isEmpty()) {
      return null;
    }
    int meio = minutos.size() / 2;
    return minutos.size() % 2 == 0
        ? (minutos.get(meio - 1) + minutos.get(meio)) / 2.0
        : minutos.get(meio);
  }
}
