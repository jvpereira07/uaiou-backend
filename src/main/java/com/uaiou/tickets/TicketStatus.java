package com.uaiou.tickets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code chamado_suporte.status} (V9__suporte.sql). */
public enum TicketStatus {
  OPEN,
  IN_PROGRESS,
  RESOLVED;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static TicketStatus fromJson(String value) {
    return TicketStatus.valueOf(value.toUpperCase());
  }
}
