package com.uaiou.notifications;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code notificacao.prioridade} (V10). RF-08.8: {@code urgente} atravessa o modo soneca. */
public enum NotificationPriority {
  NORMAL,
  URGENT;

  @JsonValue
  public String toJson() {
    return this == URGENT ? "urgent" : "normal";
  }

  @JsonCreator
  public static NotificationPriority fromJson(String value) {
    return "urgent".equalsIgnoreCase(value) ? URGENT : NORMAL;
  }
}
