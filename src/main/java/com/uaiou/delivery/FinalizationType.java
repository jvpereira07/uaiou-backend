package com.uaiou.delivery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code evidencia_entrega.tipo_finalizacao} (V7__validacao_entrega.sql). */
public enum FinalizationType {
  CODE,
  CONTESTABLE;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static FinalizationType fromJson(String value) {
    return FinalizationType.valueOf(value.toUpperCase());
  }
}
