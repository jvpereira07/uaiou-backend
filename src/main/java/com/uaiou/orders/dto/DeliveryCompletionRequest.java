package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code POST /orders/{id}/delivery/completion} — um recurso, dois modos (api/entregas.md). O
 * servidor decide a legalidade de cada um; os campos do outro modo são ignorados quando presentes.
 */
public record DeliveryCompletionRequest(
    @NotNull Mode mode,
    String deliveryCode,
    UUID proofUploadId,
    @NotNull BigDecimal lat,
    @NotNull BigDecimal lng) {

  public enum Mode {
    CODE,
    CONTESTABLE;

    @JsonValue
    public String toJson() {
      return name().toLowerCase();
    }

    @JsonCreator
    public static Mode fromJson(String value) {
      return Mode.valueOf(value.toUpperCase());
    }
  }
}
