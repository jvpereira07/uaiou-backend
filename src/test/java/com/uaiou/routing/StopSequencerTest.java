package com.uaiou.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.routing.RoutingService.Matrix;
import com.uaiou.routing.StopSequencer.Job;
import java.util.List;
import org.junit.jupiter.api.Test;

class StopSequencerTest {

  private static final int X = Matrix.UNREACHABLE;

  /**
   * Índices: 0 origem, 1 loja A, 2 destino A, 3 loja B, 4 destino B. O destino B fica colado na
   * origem: sem a restrição, ele seria visitado primeiro — antes de existir o que entregar.
   */
  private static final int[][] TEMPOS = {
    {0, 100, 300, 120, 10},
    {100, 0, 200, 30, 110},
    {300, 200, 0, 210, 290},
    {120, 30, 210, 0, 115},
    {10, 110, 290, 115, 0},
  };

  @Test
  void neverDeliversBeforePickingUp() {
    List<Integer> ordem =
        StopSequencer.sequence(new Matrix(TEMPOS, TEMPOS), List.of(new Job(1, 2), new Job(3, 4)))
            .orElseThrow();

    assertThat(ordem).containsExactly(1, 3, 4, 2);
  }

  @Test
  void anAlreadyPickedUpOrderOnlyNeedsItsDropoff() {
    List<Integer> ordem =
        StopSequencer.sequence(new Matrix(TEMPOS, TEMPOS), List.of(new Job(1, 2), new Job(null, 4)))
            .orElseThrow();

    assertThat(ordem).containsExactly(4, 1, 2);
  }

  @Test
  void unreachableLegsAreAvoidedOrMakeItInfeasible() {
    int[][] semCaminho = {
      {0, 50, X},
      {50, 0, X},
      {X, X, 0},
    };

    assertThat(StopSequencer.sequence(new Matrix(semCaminho, semCaminho), List.of(new Job(1, 2))))
        .isEmpty();
  }

  @Test
  void refusesMoreJobsThanTheExhaustiveSearchCanAfford() {
    List<Job> demais = java.util.Collections.nCopies(StopSequencer.MAX_JOBS + 1, new Job(1, 2));

    assertThat(StopSequencer.sequence(new Matrix(TEMPOS, TEMPOS), demais)).isEmpty();
  }
}
