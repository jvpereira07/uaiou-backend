package com.uaiou.credits;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code transacao_credito.tipo} (V6__transacao_credito.sql). */
public enum CreditTransactionType {
  MONTHLY_QUOTA,
  POSTING_CONSUMPTION,
  ADJUSTMENT;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static CreditTransactionType fromJson(String value) {
    return CreditTransactionType.valueOf(value.toUpperCase());
  }
}
