package com.uaiou.counteroffers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code contraoferta.status} (V5__pedido_negociacao.sql). */
public enum CounterofferStatus {
  PENDING,
  ACCEPTED,
  REJECTED,
  INVALIDATED;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static CounterofferStatus fromJson(String value) {
    return CounterofferStatus.valueOf(value.toUpperCase());
  }
}
