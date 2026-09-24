package com.uaiou.feira.service;

import com.uaiou.feira.dto.FeiraDtos.RankingResponse;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranking da feira: cada entrega concluída vale pontos inversamente proporcionais ao tempo que
 * levou. Calculado na leitura, sem tabela própria: a verdade são os pedidos finalizados, e mudar a
 * fórmula no meio do evento recalcula o placar inteiro de forma coerente.
 */
@Service
public class FeiraRankingService {

  /** Pontos de uma entrega feita em até {@link #TEMPO_REFERENCIA_SEGUNDOS}. */
  static final int PONTOS_MAXIMOS = 100;

  /** Abaixo disso o tempo não rende mais pontos: 1 minuto é "o mais rápido possível". */
  static final long TEMPO_REFERENCIA_SEGUNDOS = 60;

  /** Toda entrega concluída vale alguma coisa, por mais demorada que seja. */
  static final int PONTOS_MINIMOS = 10;

  private static final List<OrderStatus> CONCLUIDAS =
      List.of(OrderStatus.FINALIZED, OrderStatus.CONTESTABLE_FINALIZED);

  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;

  public FeiraRankingService(
      PedidoRepository pedidoRepository, EntregadorRepository entregadorRepository) {
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
  }

  /**
   * Pontos de uma entrega: 100 em até 1 min, e daí em diante proporcional ao inverso do tempo — 2
   * min valem 50, 4 min valem 25 —, nunca abaixo de 10.
   */
  static int pontosPara(long segundos) {
    long tempo = Math.max(segundos, TEMPO_REFERENCIA_SEGUNDOS);
    long pontos = Math.round((double) PONTOS_MAXIMOS * TEMPO_REFERENCIA_SEGUNDOS / tempo);
    return (int) Math.max(PONTOS_MINIMOS, pontos);
  }

  /**
   * O relógio corre da liberação no estande (coleta) até a finalização no ponto: a espera na fila
   * do balcão depende do operador, não do jogador, e não pode custar pontos. Sem coleta registrada,
   * conta desde a captura.
   */
  static long segundosDe(Pedido pedido) {
    Instant inicio = pedido.getColetadoEm() != null ? pedido.getColetadoEm() : pedido.getAceitoEm();
    if (inicio == null || pedido.getFinalizadoEm() == null) {
      return Long.MAX_VALUE;
    }
    return Math.max(0, Duration.between(inicio, pedido.getFinalizadoEm()).toSeconds());
  }

  @Transactional(readOnly = true)
  public List<RankingResponse> ranking() {
    Map<UUID, List<Pedido>> porJogador =
        pedidoRepository.findByFeiraOrigemIdIsNotNullAndStatusIn(CONCLUIDAS).stream()
            .filter(pedido -> pedido.getEntregadorId() != null)
            .collect(Collectors.groupingBy(Pedido::getEntregadorId));
    Map<UUID, Entregador> jogadores =
        entregadorRepository.findAllById(porJogador.keySet()).stream()
            .collect(Collectors.toMap(Entregador::getUsuarioId, Function.identity()));

    record Linha(UUID jogadorId, int pontos, int entregas, long melhor, long medio) {}
    List<Linha> linhas = new ArrayList<>();
    porJogador.forEach(
        (jogadorId, entregas) -> {
          List<Long> tempos = entregas.stream().map(FeiraRankingService::segundosDe).toList();
          int pontos = tempos.stream().mapToInt(FeiraRankingService::pontosPara).sum();
          List<Long> validos = tempos.stream().filter(t -> t != Long.MAX_VALUE).toList();
          long melhor = validos.stream().mapToLong(Long::longValue).min().orElse(0);
          long medio = Math.round(validos.stream().mapToLong(Long::longValue).average().orElse(0));
          linhas.add(new Linha(jogadorId, pontos, entregas.size(), melhor, medio));
        });

    // Empate em pontos: quem tem o melhor tempo fica na frente.
    linhas.sort(
        Comparator.comparingInt(Linha::pontos).reversed().thenComparingLong(Linha::melhor));

    List<RankingResponse> resposta = new ArrayList<>();
    for (int i = 0; i < linhas.size(); i++) {
      Linha linha = linhas.get(i);
      Entregador jogador = jogadores.get(linha.jogadorId());
      resposta.add(
          new RankingResponse(
              i + 1,
              linha.jogadorId(),
              jogador == null ? null : jogador.getUsuario().getNomeExibicao(),
              jogador == null ? null : jogador.getUsername(),
              linha.pontos(),
              linha.entregas(),
              linha.melhor(),
              linha.medio()));
    }
    return resposta;
  }
}
