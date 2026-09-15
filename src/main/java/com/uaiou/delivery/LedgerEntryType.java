package com.uaiou.delivery;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code lancamento_frete.tipo} (V24). O livro-razão continua um só (RF-18.2): a taxa de
 * cancelamento (RF-26.17) é devida pelo mesmo estabelecimento ao mesmo entregador, e é lida pelas
 * mesmas telas — só o rótulo muda.
 */
public enum LedgerEntryType {
  DELIVERY_FEE("delivery_fee", "frete"),
  CANCELLATION_FEE("cancellation_fee", "taxa_cancelamento");

  private final String contractName;
  private final String dbValue;

  LedgerEntryType(String contractName, String dbValue) {
    this.contractName = contractName;
    this.dbValue = dbValue;
  }

  @JsonValue
  public String contractName() {
    return contractName;
  }

  public String dbValue() {
    return dbValue;
  }

  public static LedgerEntryType fromDbValue(String value) {
    for (LedgerEntryType type : values()) {
      if (type.dbValue.equals(value)) {
        return type;
      }
    }
    throw new IllegalStateException("Tipo de lançamento desconhecido: " + value);
  }
}
