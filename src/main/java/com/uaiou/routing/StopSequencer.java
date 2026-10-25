package com.uaiou.routing;

import com.uaiou.routing.RoutingService.Matrix;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ordem das paradas de um entregador com vários pedidos ao mesmo tempo: a de menor tempo total por
 * via, com a restrição que o {@code /trip} do OSRM não conhece — <strong>a coleta de um pedido vem
 * antes da sua entrega</strong>.
 *
 * <p>Busca exaustiva com poda, de propósito: um motoboy carrega 2 ou 3 pedidos, o que dá no máximo
 * 90 ordens válidas — exato e instantâneo, sem heurística para explicar. O teto {@link #MAX_JOBS}
 * existe porque o espaço cresce fatorialmente; acima dele o problema pede um resolvedor próprio
 * (VROOM), não este.
 *
 * <p>Os índices são posições na {@link Matrix}; o índice 0 é a origem (onde o entregador está).
 */
public final class StopSequencer {

  public static final int MAX_JOBS = 4;

  private StopSequencer() {}

  /**
   * Um pedido no roteiro.
   *
   * @param pickup índice da loja na matriz, ou {@code null} quando o pedido já foi coletado — aí só
   *     resta a entrega.
   * @param dropoff índice do destino na matriz.
   */
  public record Job(Integer pickup, int dropoff) {}

  /**
   * @return índices da matriz na ordem de visita, <strong>sem</strong> a origem; vazio quando não
   *     há ordem viável (algum trecho sem caminho) ou os pedidos excedem {@link #MAX_JOBS}.
   */
  public static Optional<List<Integer>> sequence(Matrix matrix, List<Job> jobs) {
    if (jobs.isEmpty()) {
      return Optional.of(List.of());
    }
    if (jobs.size() > MAX_JOBS) {
      return Optional.empty();
    }
    Busca busca = new Busca(matrix.durationsSeconds(), jobs);
    busca.explorar(0, new boolean[jobs.size()], new boolean[jobs.size()], 0, new ArrayList<>());
    return busca.melhor == null ? Optional.empty() : Optional.of(List.copyOf(busca.melhor));
  }

  private static final class Busca {
    private final int[][] tempos;
    private final List<Job> jobs;
    private List<Integer> melhor;
    private long melhorCusto = Long.MAX_VALUE;

    Busca(int[][] tempos, List<Job> jobs) {
      this.tempos = tempos;
      this.jobs = jobs;
    }

    void explorar(
        int atual, boolean[] coletado, boolean[] entregue, long custo, List<Integer> caminho) {
      if (custo >= melhorCusto) {
        return;
      }
      boolean terminou = true;
      for (int j = 0; j < jobs.size(); j++) {
        if (entregue[j]) {
          continue;
        }
        terminou = false;
        Job job = jobs.get(j);
        boolean jaColetado = coletado[j] || job.pickup() == null;
        int proximo = jaColetado ? job.dropoff() : job.pickup();
        int trecho = tempos[atual][proximo];
        if (trecho == Matrix.UNREACHABLE) {
          continue;
        }
        caminho.add(proximo);
        if (jaColetado) {
          entregue[j] = true;
          explorar(proximo, coletado, entregue, custo + trecho, caminho);
          entregue[j] = false;
        } else {
          coletado[j] = true;
          explorar(proximo, coletado, entregue, custo + trecho, caminho);
          coletado[j] = false;
        }
        caminho.removeLast();
      }
      if (terminou) {
        melhorCusto = custo;
        melhor = new ArrayList<>(caminho);
      }
    }
  }
}
