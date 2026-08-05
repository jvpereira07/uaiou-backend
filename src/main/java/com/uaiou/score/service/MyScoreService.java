package com.uaiou.score.service;

import com.uaiou.delivery.entity.PenalidadeEstabelecimento;
import com.uaiou.delivery.repository.PenalidadeEstabelecimentoRepository;
import com.uaiou.score.dto.ScoreResponse;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code GET /me/score} — RF-20.5/RF-20.7. Só leitura do que {@link ScoreCalculationService} já
 * escreveu: nunca calcula na leitura (RF-20.3).
 */
@Service
public class MyScoreService {

  private static final String WINDOW = "all_time";

  private final EntregadorRepository entregadorRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final PenalidadeEstabelecimentoRepository penalidadeRepository;
  private final ObjectMapper objectMapper;

  public MyScoreService(
      EntregadorRepository entregadorRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      PenalidadeEstabelecimentoRepository penalidadeRepository,
      ObjectMapper objectMapper) {
    this.entregadorRepository = entregadorRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.penalidadeRepository = penalidadeRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public ScoreResponse getEntregador(UUID entregadorId) {
    Entregador entregador = entregadorRepository.findById(entregadorId).orElseThrow();
    return new ScoreResponse(
        entregador.getScore(),
        WINDOW,
        entregador.getScoreCalculadoEm(),
        componentes(entregador.getScoreComponentes()),
        null);
  }

  /** RF-20.6 — o estabelecimento também vê cada penalidade, com motivo, pedido e data. */
  @Transactional(readOnly = true)
  public ScoreResponse getEstabelecimento(UUID estabelecimentoId) {
    Estabelecimento estabelecimento =
        estabelecimentoRepository.findById(estabelecimentoId).orElseThrow();
    List<PenalidadeEstabelecimento> penalidades =
        penalidadeRepository.findByEstabelecimentoIdOrderByCriadoEmDesc(estabelecimentoId);

    return new ScoreResponse(
        estabelecimento.getScore(),
        WINDOW,
        estabelecimento.getScoreCalculadoEm(),
        componentes(estabelecimento.getScoreComponentes()),
        penalidades.stream()
            .map(
                penalidade ->
                    new ScoreResponse.Penalty(
                        penalidade.getMotivo(), penalidade.getPedidoId(), penalidade.getCriadoEm()))
            .toList());
  }

  private List<ScoreResponse.Component> componentes(String json) {
    if (json == null) {
      return List.of();
    }
    return objectMapper.readValue(json, new TypeReference<List<ScoreResponse.Component>>() {});
  }
}
