package com.uaiou.delivery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code lancamento_frete.status} (V8__livro_razao_avaliacao.sql). Confirmação de acerto é T-18.
 */
public enum LedgerStatus {
  RECEIVABLE,
  SETTLED;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static LedgerStatus fromJson(String value) {
    return LedgerStatus.valueOf(value.toUpperCase());
  }
}
