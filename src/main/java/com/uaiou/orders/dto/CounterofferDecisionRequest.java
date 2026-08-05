package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.uaiou.counteroffers.CounterofferStatus;
import jakarta.validation.constraints.NotNull;

/** {@code PUT /counteroffers/{id}/decision} (RF-14.5) — um recurso cobre aceitar e recusar. */
public record CounterofferDecisionRequest(@NotNull Outcome outcome) {

  /** Vocabulário do contrato (api/pedidos.md): {@code "accepted"}/{@code "rejected"}. */
  public enum Outcome {
    ACCEPTED,
    REJECTED;

    @JsonValue
    public String toJson() {
      return name().toLowerCase();
    }

    @JsonCreator
    public static Outcome fromJson(String value) {
      return Outcome.valueOf(value.toUpperCase());
    }

    public CounterofferStatus toStatus() {
      return this == ACCEPTED ? CounterofferStatus.ACCEPTED : CounterofferStatus.REJECTED;
    }
  }
}
