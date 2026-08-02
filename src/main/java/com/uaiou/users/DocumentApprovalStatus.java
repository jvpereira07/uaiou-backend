package com.uaiou.users;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code documento_cadastro.status_aprovacao} (V3/V15__documento_cadastro_status_superado.sql). */
public enum DocumentApprovalStatus {
  PENDING,
  APPROVED,
  REJECTED,
  /**
   * Substituído por um reenvio (RF-06.4/RF-06.5) — nunca foi negado, só deixou de ser a versão
   * vigente.
   */
  SUPERSEDED;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static DocumentApprovalStatus fromJson(String value) {
    return DocumentApprovalStatus.valueOf(value.toUpperCase());
  }
}
