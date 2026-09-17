package com.uaiou.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code pedido.status} (V5__pedido_negociacao.sql). T-11 só produz {@link #CREATED} (efêmero,
 * dentro da transação de criação) e {@link #PUBLISHED}; os demais pertencem a T-13/T-14/T-15/T-17 e
 * existem aqui porque o enum precisa cobrir o CHECK do banco por inteiro.
 *
 * <p>Sem {@code em_disputa}: a v1 não custodia dinheiro, então não há o que a arbitragem
 * redistribuiria (escopo-v1.md).
 */
public enum OrderStatus {
  CREATED,
  PUBLISHED,
  IN_NEGOTIATION,
  ACCEPTED,
  /**
   * T-26 — estabelecimento confirmou a entrega do pacote ao entregador; pré-condição da
   * finalização.
   */
  PICKED_UP,
  FINALIZED,
  CONTESTABLE_FINALIZED,
  CANCELLED;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static OrderStatus fromJson(String value) {
    return OrderStatus.valueOf(value.toUpperCase());
  }
}
