package com.uaiou.users;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code entregador.forma_pagamento} (V22) — como o entregador aceita receber pelo frete. */
public enum PaymentMethod {
  CASH,
  CREDIT,
  DEBIT,
  PIX;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static PaymentMethod fromJson(String value) {
    return PaymentMethod.valueOf(value.toUpperCase());
  }
}
