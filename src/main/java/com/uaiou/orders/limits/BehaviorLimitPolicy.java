package com.uaiou.orders.limits;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aritmética comum a todo limite de comportamento. Ao atingir o máximo dentro da janela, o bloqueio
 * dura {@code bloqueio} a partir da ocorrência que completou a conta; passado o bloqueio, a próxima
 * ocorrência ainda dentro da janela bloqueia de novo (mesma semântica de RF-26.29).
 *
 * <p>Quem chama decide o que é ocorrência — a política só recebe a busca, já recortada pela janela.
 */
@Component
public class BehaviorLimitPolicy {

  private final LimiteComportamentoRepository repository;

  public BehaviorLimitPolicy(LimiteComportamentoRepository repository) {
    this.repository = repository;
  }

  /**
   * @param ocorrenciasDesde dado o início da janela, devolve os instantes das ocorrências nela.
   * @return até quando o sujeito está bloqueado, ou vazio se não está (ou se a regra está
   *     desligada).
   */
  @Transactional(readOnly = true)
  public Optional<Instant> bloqueadoAte(
      BehaviorLimitRule regra, Function<Instant, List<Instant>> ocorrenciasDesde) {
    LimiteComportamento limite = repository.findById(regra.dbKey()).orElse(null);
    if (limite == null || !limite.isAtivo()) {
      return Optional.empty();
    }
    Instant agora = Instant.now();
    List<Instant> recentes = ocorrenciasDesde.apply(agora.minus(limite.getJanela()));
    if (recentes.size() < limite.getMaximo()) {
      return Optional.empty();
    }
    Instant ultima = recentes.stream().max(Instant::compareTo).orElseThrow();
    Instant liberadoEm = ultima.plus(limite.getBloqueio());
    return agora.isBefore(liberadoEm) ? Optional.of(liberadoEm) : Optional.empty();
  }
}
