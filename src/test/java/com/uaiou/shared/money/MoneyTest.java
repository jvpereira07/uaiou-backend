package com.uaiou.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Teste unitário de fumaça exigido por RF-01.13 (nível unitário, sem contexto Spring) — e, ao mesmo
 * tempo, a prova do critério de aceite 7: dinheiro nunca vira float em nenhum ponto do caminho.
 */
class MoneyTest {

  @Test
  void classicFloatingPointFailureDoesNotHappen() {
    // 0.10 + 0.20 em double resulta em 0.30000000000000004 — o motivo de este tipo existir.
    Money result = Money.of("0.10").add(Money.of("0.20"));

    assertThat(result).isEqualTo(Money.of("0.30"));
    assertThat(result.toString()).isEqualTo("0.30");
  }

  @Test
  void alwaysNormalizesToTwoDecimalPlaces() {
    assertThat(Money.of("6").toString()).isEqualTo("6.00");
    assertThat(Money.of("6.1").toString()).isEqualTo("6.10");
  }

  @Test
  void roundsHalfUpOnConstruction() {
    assertThat(Money.of("1.005").toString()).isEqualTo("1.01");
    assertThat(Money.of("1.004").toString()).isEqualTo("1.00");
  }

  @Test
  void neverSerializesInScientificNotation() {
    Money tiny = Money.of(new BigDecimal("0.000001"));

    assertThat(tiny.toString()).doesNotContain("E");
  }

  @Test
  void subtractAndMultiplyPreserveScale() {
    Money result = Money.of("10.00").subtract(Money.of("3.33")).multiply(3);

    assertThat(result).isEqualTo(Money.of("20.01"));
  }

  @Test
  void signChecks() {
    assertThat(Money.of("5.00").isPositive()).isTrue();
    assertThat(Money.of("-5.00").isNegative()).isTrue();
    assertThat(Money.zero().isZero()).isTrue();
  }

  @Test
  void equalityIsByValueNotByInstance() {
    assertThat(Money.of("6.00")).isEqualTo(Money.of(new BigDecimal("6.0")));
    assertThat(Money.of("6.00").hashCode()).isEqualTo(Money.of(new BigDecimal("6.0")).hashCode());
  }

  @Test
  void orderingFollowsNumericValue() {
    assertThat(Money.of("5.00").compareTo(Money.of("10.00"))).isNegative();
  }

  @Test
  void rejectsGarbageInput() {
    assertThatThrownBy(() -> Money.of("not-a-number")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullInput() {
    assertThatThrownBy(() -> Money.of((String) null)).isInstanceOf(NullPointerException.class);
  }
}
