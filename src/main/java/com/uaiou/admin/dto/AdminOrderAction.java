package com.uaiou.admin.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.uaiou.orders.OrderStatus;
import java.util.Set;

/**
 * Intervenções manuais do admin sobre o estado do pedido. Cada uma declara de onde pode partir — a
 * mesma tabela alimenta a validação no backend e os botões do painel ({@code availableActions}).
 */
public enum AdminOrderAction {
  /** Qualquer estado em andamento vira cancelado; sem taxa ao estabelecimento. */
  CANCEL(
      Set.of(
          OrderStatus.PUBLISHED,
          OrderStatus.IN_NEGOTIATION,
          OrderStatus.ACCEPTED,
          OrderStatus.PICKED_UP)),
  /** Tira do entregador (mesmo já coletado) e devolve à vitrine. */
  RETURN_TO_SHOWCASE(Set.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP)),
  /** Confirma a coleta no lugar do estabelecimento. */
  MARK_PICKED_UP(Set.of(OrderStatus.ACCEPTED)),
  /** Finaliza a entrega confirmada fora do app, ou antecipa a consolidação do contestável. */
  FINALIZE(Set.of(OrderStatus.PICKED_UP, OrderStatus.CONTESTABLE_FINALIZED));

  private final Set<OrderStatus> origens;

  AdminOrderAction(Set<OrderStatus> origens) {
    this.origens = origens;
  }

  public boolean permitidaEm(OrderStatus status) {
    return origens.contains(status);
  }

  @JsonValue
  public String code() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static AdminOrderAction fromCode(String value) {
    return AdminOrderAction.valueOf(value.toUpperCase());
  }
}
