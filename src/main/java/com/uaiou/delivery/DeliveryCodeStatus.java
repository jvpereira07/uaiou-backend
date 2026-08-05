package com.uaiou.delivery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code otp.status} (V7__validacao_entrega.sql). Vocabulário do contrato (api/entregas.md) usa
 * {@code "issued"} onde o banco guarda {@code "gerado"} — mesma convenção DB-português/API-inglês
 * do resto do sistema.
 */
public enum DeliveryCodeStatus {
  ISSUED,
  VALIDATED,
  EXPIRED,
  BLOCKED;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static DeliveryCodeStatus fromJson(String value) {
    return DeliveryCodeStatus.valueOf(value.toUpperCase());
  }
}
