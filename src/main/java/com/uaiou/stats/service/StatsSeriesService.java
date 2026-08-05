package com.uaiou.stats.service;

import com.uaiou.delivery.LedgerStatus;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.stats.dto.SeriesResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /me/stats/series} (RF-22.6). Métricas suportadas nesta v1: {@code deliveries} e {@code
 * earnings} (entregador), {@code orders_published} e {@code orders_completed} (estabelecimento) — o
 * conjunto que dá para agregar diretamente das tabelas existentes sem cache dedicado (RF-22.8).
 */
@Service
public class StatsSeriesService {

  private static final int DEFAULT_DAYS = 30;
  private static final List<OrderStatus> FINALIZADOS =
      List.of(OrderStatus.FINALIZED, OrderStatus.CONTESTABLE_FINALIZED);

  private final PedidoRepository pedidoRepository;
  private final LancamentoFreteRepository lancamentoFreteRepository;

  public StatsSeriesService(
      PedidoRepository pedidoRepository, LancamentoFreteRepository lancamentoFreteRepository) {
    this.pedidoRepository = pedidoRepository;
    this.lancamentoFreteRepository = lancamentoFreteRepository;
  }

  @Transactional(readOnly = true)
  public SeriesResponse courierSeries(UUID entregadorId, String metric, String granularity) {
    Instant desde = Instant.now().minus(java.time.Duration.ofDays(DEFAULT_DAYS));
    Instant agora = Instant.now();

    return switch (metric) {
      case "deliveries" -> {
        List<Pedido> finalizados =
            pedidoRepository
                .findByEntregadorIdAndCriadoEmBetween(entregadorId, desde, agora)
                .stream()
                .filter(p -> FINALIZADOS.contains(p.getStatus()))
                .toList();
        yield serie(
            metric,
            granularity,
            desde,
            agora,
            contarPorDia(finalizados.stream().map(Pedido::getCriadoEm).toList()));
      }
      case "earnings" -> {
        List<LancamentoFrete> lancamentos =
            lancamentoFreteRepository.findByEntregadorIdAndCriadoEmBetween(
                entregadorId, desde, agora);
        yield serie(metric, granularity, desde, agora, somarPorDia(lancamentos));
      }
      default ->
          throw new BadRequestException(
              "UNSUPPORTED_METRIC", "Métricas suportadas para entregador: deliveries, earnings.");
    };
  }

  @Transactional(readOnly = true)
  public SeriesResponse merchantSeries(UUID estabelecimentoId, String metric, String granularity) {
    Instant desde = Instant.now().minus(java.time.Duration.ofDays(DEFAULT_DAYS));
    Instant agora = Instant.now();
    List<Pedido> pedidos =
        pedidoRepository.findByEstabelecimentoIdAndCriadoEmBetween(estabelecimentoId, desde, agora);

    return switch (metric) {
      case "orders_published" ->
          serie(
              metric,
              granularity,
              desde,
              agora,
              contarPorDia(
                  pedidos.stream()
                      .filter(p -> p.getStatus() != OrderStatus.CREATED)
                      .map(Pedido::getCriadoEm)
                      .toList()));
      case "orders_completed" ->
          serie(
              metric,
              granularity,
              desde,
              agora,
              contarPorDia(
                  pedidos.stream()
                      .filter(
                          p -> FINALIZADOS.contains(p.getStatus()) && p.getFinalizadoEm() != null)
                      .map(Pedido::getFinalizadoEm)
                      .toList()));
      default ->
          throw new BadRequestException(
              "UNSUPPORTED_METRIC",
              "Métricas suportadas para estabelecimento: orders_published, orders_completed.");
    };
  }

  private Map<LocalDate, Long> contarPorDia(List<Instant> instantes) {
    return instantes.stream().collect(Collectors.groupingBy(this::dia, Collectors.counting()));
  }

  private Map<LocalDate, Long> somarPorDia(List<LancamentoFrete> lancamentos) {
    // Contagem, não soma monetária: a série é number-only (BigDecimal do ponto), e o valor
    // agregado por dia já aparece no resumo (RF-22.3). Aqui expomos a contagem de lançamentos/dia.
    return lancamentos.stream()
        .filter(
            l -> l.getStatus() == LedgerStatus.RECEIVABLE || l.getStatus() == LedgerStatus.SETTLED)
        .collect(Collectors.groupingBy(l -> dia(l.getCriadoEm()), Collectors.counting()));
  }

  private LocalDate dia(Instant instante) {
    return instante.atZone(ZoneOffset.UTC).toLocalDate();
  }

  /** RF-22.6 — dia sem atividade retorna 0, nunca é omitido. */
  private SeriesResponse serie(
      String metric, String granularity, Instant desde, Instant ate, Map<LocalDate, Long> porDia) {
    LocalDate inicio = dia(desde);
    LocalDate fim = dia(ate);
    List<SeriesResponse.Point> pontos = new ArrayList<>();
    for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
      pontos.add(new SeriesResponse.Point(dia, BigDecimal.valueOf(porDia.getOrDefault(dia, 0L))));
    }
    return new SeriesResponse(metric, granularity == null ? "day" : granularity, pontos);
  }
}
