package com.uaiou.delivery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@code contingencia_otp.canal} (V7__validacao_entrega.sql): {@code sms}/{@code estabelecimento}.
 */
public enum ContingencyChannel {
  SMS,
  MERCHANT;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static ContingencyChannel fromJson(String value) {
    return ContingencyChannel.valueOf(value.toUpperCase());
  }
}
