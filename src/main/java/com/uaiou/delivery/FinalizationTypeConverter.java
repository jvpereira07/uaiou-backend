package com.uaiou.delivery;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * DB usa {@code otp}/{@code contestavel} (nome histórico da coluna); API usa {@code code}/{@code
 * contestable}.
 */
@Converter(autoApply = true)
public class FinalizationTypeConverter implements AttributeConverter<FinalizationType, String> {

  @Override
  public String convertToDatabaseColumn(FinalizationType attribute) {
    if (attribute == null) {
      return null;
    }
    return attribute == FinalizationType.CODE ? "otp" : "contestavel";
  }

  @Override
  public FinalizationType convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "otp" -> FinalizationType.CODE;
      case "contestavel" -> FinalizationType.CONTESTABLE;
      default -> throw new IllegalStateException("Tipo de finalização desconhecido: " + dbData);
    };
  }
}
