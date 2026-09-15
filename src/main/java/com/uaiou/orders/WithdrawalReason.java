package com.uaiou.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** RF-26.25 — motivo fechado da desistência do entregador. Persistido pelo nome do contrato. */
public enum WithdrawalReason {
  VEHICLE_PROBLEM("vehicle_problem"),
  ACCIDENT("accident"),
  WRONG_ORDER("wrong_order"),
  /** RF-26.30 — o único motivo que pode deixar de penalizar, se a espera na loja passou do tolerado. */
  PICKUP_DELAY("pickup_delay"),
  PERSONAL("personal"),
  OTHER("other");

  private final String code;

  WithdrawalReason(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  @JsonCreator
  public static WithdrawalReason fromCode(String value) {
    for (WithdrawalReason reason : values()) {
      if (reason.code.equals(value)) {
        return reason;
      }
    }
    throw new IllegalArgumentException("Motivo de desistência desconhecido: " + value);
  }
}
