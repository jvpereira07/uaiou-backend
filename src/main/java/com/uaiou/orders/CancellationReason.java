package com.uaiou.orders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * RF-26.16 — motivo fechado do cancelamento pelo estabelecimento. É dado, não enfeite: é o que
 * permite ver quem cancela demais e por quê. Persistido pelo nome do contrato.
 */
public enum CancellationReason {
  CUSTOMER_GAVE_UP("customer_gave_up"),
  PAYMENT_DECLINED("payment_declined"),
  OUT_OF_STOCK("out_of_stock"),
  ORDER_ERROR("order_error"),
  COURIER_DELAY("courier_delay"),
  OTHER("other");

  private final String code;

  CancellationReason(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  @JsonCreator
  public static CancellationReason fromCode(String value) {
    for (CancellationReason reason : values()) {
      if (reason.code.equals(value)) {
        return reason;
      }
    }
    throw new IllegalArgumentException("Motivo de cancelamento desconhecido: " + value);
  }
}
