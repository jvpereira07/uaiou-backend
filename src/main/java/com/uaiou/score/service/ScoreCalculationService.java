package com.uaiou.score.service;

import com.uaiou.delivery.repository.PenalidadeEstabelecimentoRepository;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.repository.DesistenciaPedidoRepository;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.reviews.entity.Avaliacao;
import com.uaiou.reviews.repository.AvaliacaoRepository;
import com.uaiou.score.config.ScoreProperties;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * RF-20.1/RF-20.3 — cálculo por evento, nunca na leitura: {@code GET /me/score} só lê o que este
 * serviço já escreveu (RF-20.7 reforça: não existe rota de escrita, correção se faz corrigindo o
 * insumo, não o número).
 *
 * <p>RF-20.2 é o invariante mais importante: nada aqui usa {@code PenalidadeEstabelecimento} para
 * tocar o score do entregador. A separação é estrutural — os dois métodos nem compartilham a
 * consulta de penalidade.
 */
@Service
public class ScoreCalculationService {

  private static final List<OrderStatus> ENVOLVEM_ACEITE =
      List.of(
          OrderStatus.ACCEPTED,
          OrderStatus.PICKED_UP,
          OrderStatus.FINALIZED,
          OrderStatus.CONTESTABLE_FINALIZED);
  private static final List<OrderStatus> FINALIZADOS =
      List.of(OrderStatus.FINALIZED, OrderStatus.CONTESTABLE_FINALIZED);

  private static final BigDecimal PESO_AVALIACAO = BigDecimal.valueOf(0.6);
  private static final BigDecimal PESO_CONCLUSAO = BigDecimal.valueOf(0.4);
  private static final BigDecimal PESO_TAXA_CONTINGENCIA = BigDecimal.valueOf(0.5);
  private static final BigDecimal PESO_AVALIACAO_ESTABELECIMENTO = BigDecimal.valueOf(0.5);

  private final EntregadorRepository entregadorRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final AvaliacaoRepository avaliacaoRepository;
  private final PedidoRepository pedidoRepository;
  private final PenalidadeEstabelecimentoRepository penalidadeRepository;
  private final ScoreProperties properties;
  private final ObjectMapper objectMapper;
  private final DesistenciaPedidoRepository desistenciaRepository;

  public ScoreCalculationService(
      EntregadorRepository entregadorRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      AvaliacaoRepository avaliacaoRepository,
      PedidoRepository pedidoRepository,
      PenalidadeEstabelecimentoRepository penalidadeRepository,
      ScoreProperties properties,
      ObjectMapper objectMapper,
      DesistenciaPedidoRepository desistenciaRepository) {
    this.desistenciaRepository = desistenciaRepository;
    this.entregadorRepository = entregadorRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.avaliacaoRepository = avaliacaoRepository;
    this.pedidoRepository = pedidoRepository;
    this.penalidadeRepository = penalidadeRepository;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  /**
   * RF-20.1 — composição do entregador: avaliação + taxa de conclusão. O terceiro insumo do
   * catálogo da task ("contingências atribuíveis a ele") não tem produtor neste backlog: RN-09.5
   * (RF-16.6) desenha a contingência para nunca tocar o entregador, então não existe evento que
   * alimentasse esse componente sem inventar uma fonte de dados fora do que foi especificado.
   * Registrado aqui como componente com peso zero, não omitido em silêncio.
   */
  @Transactional
  public void recalcularEntregador(UUID entregadorId) {
    Entregador entregador = entregadorRepository.findById(entregadorId).orElse(null);
    if (entregador == null) {
      return;
    }

    List<Avaliacao> avaliacoes = avaliacaoRepository.findByAlvoId(entregadorId);
    BigDecimal avaliacaoValue = mediaPonderada(avaliacoes);

    List<OrderStatus> todosEnvolvidos = ENVOLVEM_ACEITE;
    // RF-26.31 — desistência que conta é aceite não finalizado; depois da chegada pesa em dobro,
    // porque a loja provavelmente já separou o pacote. O pedido desistido não está mais atribuído a
    // ele, então o insumo vem do registro da desistência, não do pedido.
    long pesoDesistencias =
        desistenciaRepository.findByEntregadorIdAndContaPenalidadeTrue(entregadorId).stream()
            .mapToLong(desistencia -> desistencia.getChegouEm() == null ? 1 : 2)
            .sum();
    long aceitos =
        pedidoRepository.findByEntregadorIdAndStatusIn(entregadorId, todosEnvolvidos).size()
            + pesoDesistencias;
    long finalizados =
        pedidoRepository.findByEntregadorIdAndStatusIn(entregadorId, FINALIZADOS).size();
    BigDecimal conclusaoValue =
        aceitos == 0 ? null : normalizarTaxaParaEscala5(finalizados, aceitos);

    List<ScoreResponseComponent> componentes = new ArrayList<>();
    componentes.add(new ScoreResponseComponent("avaliacao", avaliacaoValue, PESO_AVALIACAO));
    componentes.add(new ScoreResponseComponent("taxa_conclusao", conclusaoValue, PESO_CONCLUSAO));
    componentes.add(new ScoreResponseComponent("contingencia_atribuivel", null, BigDecimal.ZERO));

    persistir(componentes, entregador::atualizarScore);
  }

  /**
   * RF-20.1 — composição do estabelecimento: avaliação + taxa de contingência de código (métrica-
   * âncora, RN-09.3). Cada penalidade em {@code PenalidadeEstabelecimento} já é, por desenho
   * (T-16), atribuição objetiva de falha — não há julgamento a refazer aqui, só agregação.
   */
  @Transactional
  public void recalcularEstabelecimento(UUID estabelecimentoId) {
    Estabelecimento estabelecimento =
        estabelecimentoRepository.findById(estabelecimentoId).orElse(null);
    if (estabelecimento == null) {
      return;
    }

    List<Avaliacao> avaliacoes = avaliacaoRepository.findByAlvoId(estabelecimentoId);
    BigDecimal avaliacaoValue = mediaPonderada(avaliacoes);

    long finalizados =
        pedidoRepository.findByEstabelecimentoIdAndStatusIn(estabelecimentoId, FINALIZADOS).size();
    int penalidades = penalidadeRepository.countByEstabelecimentoId(estabelecimentoId);
    // Menos contingência é melhor: taxa 0 -> nota 5; taxa >= 100% -> nota 1.
    BigDecimal taxaContingenciaValue =
        finalizados == 0 ? null : normalizarTaxaInversaParaEscala5(penalidades, finalizados);

    List<ScoreResponseComponent> componentes = new ArrayList<>();
    componentes.add(
        new ScoreResponseComponent("avaliacao", avaliacaoValue, PESO_AVALIACAO_ESTABELECIMENTO));
    componentes.add(
        new ScoreResponseComponent(
            "taxa_contingencia", taxaContingenciaValue, PESO_TAXA_CONTINGENCIA));

    persistir(componentes, estabelecimento::atualizarScore);
  }

  /** RF-19.6 — automáticas (ativa=false) pesam menos que opinião real, nunca zero. */
  private BigDecimal mediaPonderada(List<Avaliacao> avaliacoes) {
    if (avaliacoes.isEmpty()) {
      return null;
    }
    BigDecimal pesoInativa = BigDecimal.valueOf(properties.inactiveReviewWeight());
    BigDecimal somaPonderada = BigDecimal.ZERO;
    BigDecimal somaPesos = BigDecimal.ZERO;
    for (Avaliacao avaliacao : avaliacoes) {
      BigDecimal peso = avaliacao.isAtiva() ? BigDecimal.ONE : pesoInativa;
      somaPonderada = somaPonderada.add(BigDecimal.valueOf(avaliacao.getNota()).multiply(peso));
      somaPesos = somaPesos.add(peso);
    }
    if (somaPesos.signum() == 0) {
      return null;
    }
    return somaPonderada.divide(somaPesos, 2, RoundingMode.HALF_UP);
  }

  private BigDecimal normalizarTaxaParaEscala5(long numerador, long denominador) {
    BigDecimal taxa =
        BigDecimal.valueOf(numerador)
            .divide(BigDecimal.valueOf(denominador), 4, RoundingMode.HALF_UP);
    // 0% -> 1, 100% -> 5.
    return BigDecimal.ONE
        .add(taxa.multiply(BigDecimal.valueOf(4)))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal normalizarTaxaInversaParaEscala5(long numerador, long denominador) {
    BigDecimal taxa =
        BigDecimal.valueOf(numerador)
            .divide(BigDecimal.valueOf(denominador), 4, RoundingMode.HALF_UP);
    BigDecimal limitada = taxa.min(BigDecimal.ONE);
    // 0% -> 5, 100% -> 1.
    return BigDecimal.valueOf(5)
        .subtract(limitada.multiply(BigDecimal.valueOf(4)))
        .setScale(2, RoundingMode.HALF_UP);
  }

  private void persistir(
      List<ScoreResponseComponent> componentes, TriConsumer<BigDecimal, String, Instant> aplicar) {
    BigDecimal somaPonderada = BigDecimal.ZERO;
    BigDecimal somaPesos = BigDecimal.ZERO;
    List<Map3> comContribuicao = new ArrayList<>();
    for (ScoreResponseComponent componente : componentes) {
      BigDecimal contribuicao =
          componente.value == null ? null : componente.value.multiply(componente.weight);
      if (componente.value != null && componente.weight.signum() > 0) {
        somaPonderada = somaPonderada.add(contribuicao);
        somaPesos = somaPesos.add(componente.weight);
      }
      comContribuicao.add(
          new Map3(componente.name, componente.value, componente.weight, contribuicao));
    }

    BigDecimal valorFinal =
        somaPesos.signum() == 0 ? null : somaPonderada.divide(somaPesos, 2, RoundingMode.HALF_UP);
    Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
    String json = objectMapper.writeValueAsString(comContribuicao);
    aplicar.accept(valorFinal, json, agora);
  }

  private record ScoreResponseComponent(String name, BigDecimal value, BigDecimal weight) {}

  private record Map3(String name, BigDecimal value, BigDecimal weight, BigDecimal contribution) {}

  @FunctionalInterface
  private interface TriConsumer<A, B, C> {
    void accept(A a, B b, C c);
  }
}
