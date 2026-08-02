package com.uaiou.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário do sistema. Nunca é representado como {@code float}/{@code double} em nenhum
 * ponto do caminho — nem no JSON (serializa como string decimal, ver {@link MoneySerializer}), nem
 * no banco (ver {@link MoneyAttributeConverter}, mapeado para {@code numeric(12,2)}).
 *
 * <p>Sempre normalizado para 2 casas decimais com arredondamento {@link RoundingMode#HALF_UP}. Por
 * isso {@code equals}/{@code hashCode} gerados pelo record (que comparam o {@link BigDecimal}
 * interno) são seguros: duas instâncias com o mesmo valor sempre têm a mesma escala.
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

  public static final Money ZERO = new Money(BigDecimal.ZERO);

  public Money {
    Objects.requireNonNull(amount, "amount");
    amount = amount.setScale(SCALE, ROUNDING);
  }

  public static Money of(BigDecimal amount) {
    return new Money(amount);
  }

  public static Money of(String amount) {
    Objects.requireNonNull(amount, "amount");
    try {
      return new Money(new BigDecimal(amount));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Valor monetário inválido: \"" + amount + "\"", e);
    }
  }

  public static Money zero() {
    return ZERO;
  }

  public Money add(Money other) {
    return new Money(this.amount.add(other.amount));
  }

  public Money subtract(Money other) {
    return new Money(this.amount.subtract(other.amount));
  }

  public Money multiply(int factor) {
    return new Money(this.amount.multiply(BigDecimal.valueOf(factor)));
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  @Override
  public int compareTo(Money other) {
    return this.amount.compareTo(other.amount);
  }

  /**
   * Forma canônica para exibição/log — sempre decimal simples (ex.: "6.00"), nunca notação
   * científica.
   */
  @Override
  public String toString() {
    return amount.toPlainString();
  }
}
