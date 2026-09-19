package com.uaiou.orders.timeout;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.uaiou.orders.OrderStatus;
import java.util.List;

/**
 * Regras de timeout do ciclo de vida (V26). Cada uma tem um marco de referência e uma ação fixa: o
 * admin ajusta prazo e liga/desliga, mas não troca o que a regra faz — uma regra que cancela pedido
 * coletado por engano seria um incidente, não uma configuração.
 */
public enum OrderTimeoutRule {
  /** Publicado (ou em negociação) sem aceite desde a criação: cancela. */
  UNACCEPTED(
      "publicado_sem_aceite",
      "unaccepted",
      TimeoutAction.CANCEL,
      List.of(OrderStatus.PUBLISHED, OrderStatus.IN_NEGOTIATION)),
  /** Aceito e sem coleta desde o aceite: tira do entregador e devolve à vitrine. */
  NOT_PICKED_UP(
      "aceito_sem_coleta",
      "not_picked_up",
      TimeoutAction.RETURN_TO_SHOWCASE,
      List.of(OrderStatus.ACCEPTED)),
  /**
   * Coletado e sem finalização desde a coleta: só sinaliza. O pacote está com o entregador — nem
   * cancelar nem devolver resolvem isso; é caso para o suporte olhar.
   */
  NOT_DELIVERED(
      "coletado_sem_finalizacao",
      "not_delivered",
      TimeoutAction.FLAG,
      List.of(OrderStatus.PICKED_UP));

  private final String dbKey;
  private final String code;
  private final TimeoutAction action;
  private final List<OrderStatus> statuses;

  OrderTimeoutRule(String dbKey, String code, TimeoutAction action, List<OrderStatus> statuses) {
    this.dbKey = dbKey;
    this.code = code;
    this.action = action;
    this.statuses = statuses;
  }

  public String dbKey() {
    return dbKey;
  }

  @JsonValue
  public String code() {
    return code;
  }

  public TimeoutAction action() {
    return action;
  }

  public List<OrderStatus> statuses() {
    return statuses;
  }

  public static OrderTimeoutRule fromDbKey(String value) {
    for (OrderTimeoutRule rule : values()) {
      if (rule.dbKey.equals(value)) {
        return rule;
      }
    }
    throw new IllegalStateException("Regra de timeout desconhecida: " + value);
  }

  @JsonCreator
  public static OrderTimeoutRule fromCode(String value) {
    for (OrderTimeoutRule rule : values()) {
      if (rule.code.equals(value)) {
        return rule;
      }
    }
    throw new IllegalArgumentException("Regra de timeout desconhecida: " + value);
  }

  public enum TimeoutAction {
    CANCEL("cancelado"),
    RETURN_TO_SHOWCASE("devolvido_vitrine"),
    FLAG("sinalizado");

    private final String dbValue;

    TimeoutAction(String dbValue) {
      this.dbValue = dbValue;
    }

    public String dbValue() {
      return dbValue;
    }

    @JsonValue
    public String code() {
      return name().toLowerCase();
    }

    public static TimeoutAction fromDbValue(String value) {
      for (TimeoutAction action : values()) {
        if (action.dbValue.equals(value)) {
          return action;
        }
      }
      throw new IllegalStateException("Ação de timeout desconhecida: " + value);
    }
  }
}
