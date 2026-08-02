package com.uaiou.admin.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** {@code decision} de {@code PUT /admin/registrations/{userId}/review} (RF-07.4). */
public enum ReviewDecision {
  APPROVED,
  REJECTED;

  @JsonValue
  public String toJson() {
    return name().toLowerCase();
  }

  @JsonCreator
  public static ReviewDecision fromJson(String value) {
    return ReviewDecision.valueOf(value.toUpperCase());
  }
}
