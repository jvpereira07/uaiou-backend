package com.uaiou.users;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code sancao.tipo} (T-07) — suspensão tem prazo, banimento não (ck_sancao_fim_coerente). */
public enum SanctionType {
  SUSPENSION,
  BAN;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static SanctionType fromJson(String value) {
    return SanctionType.valueOf(value.toUpperCase());
  }
}
