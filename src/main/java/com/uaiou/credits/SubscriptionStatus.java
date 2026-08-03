package com.uaiou.credits;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code assinatura.status} — só {@link #ACTIVE} é produzido em T-09 (nenhuma rota de cancelamento
 * ou inadimplência existe na v1, ver escopo-v1.md); os outros dois valores existem para o converter
 * nunca falhar contra uma linha legada e para o schema (V4) já modelado não ficar incoerente com o
 * enum.
 */
public enum SubscriptionStatus {
  ACTIVE,
  CANCELLED,
  PAST_DUE;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static SubscriptionStatus fromJson(String value) {
    return SubscriptionStatus.valueOf(value.toUpperCase());
  }
}
