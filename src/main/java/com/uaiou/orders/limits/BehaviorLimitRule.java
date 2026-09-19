package com.uaiou.orders.limits;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Limites de comportamento (V27): "N ocorrências dentro da janela bloqueiam por um tempo". O que
 * conta como ocorrência e o que o bloqueio impede são fixos por regra; o admin só calibra os
 * números.
 */
public enum BehaviorLimitRule {
  /** RF-26.29 — desistências que contam penalidade. Bloqueia o entregador de ACEITAR pedidos. */
  COURIER_WITHDRAWALS("desistencia_entregador", "courier_withdrawals"),
  /**
   * Cancelamentos feitos pelo próprio estabelecimento (não pela plataforma). Bloqueia a loja de
   * PUBLICAR pedidos.
   */
  MERCHANT_CANCELLATIONS("cancelamento_estabelecimento", "merchant_cancellations");

  private final String dbKey;
  private final String code;

  BehaviorLimitRule(String dbKey, String code) {
    this.dbKey = dbKey;
    this.code = code;
  }

  public String dbKey() {
    return dbKey;
  }

  @JsonValue
  public String code() {
    return code;
  }

  public static BehaviorLimitRule fromDbKey(String value) {
    for (BehaviorLimitRule rule : values()) {
      if (rule.dbKey.equals(value)) {
        return rule;
      }
    }
    throw new IllegalStateException("Limite de comportamento desconhecido: " + value);
  }

  @JsonCreator
  public static BehaviorLimitRule fromCode(String value) {
    for (BehaviorLimitRule rule : values()) {
      if (rule.code.equals(value)) {
        return rule;
      }
    }
    throw new IllegalArgumentException("Limite de comportamento desconhecido: " + value);
  }
}
