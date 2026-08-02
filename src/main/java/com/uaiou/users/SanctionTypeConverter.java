package com.uaiou.users;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SanctionTypeConverter implements AttributeConverter<SanctionType, String> {

  @Override
  public String convertToDatabaseColumn(SanctionType attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case SUSPENSION -> "suspensao";
      case BAN -> "banimento";
    };
  }

  @Override
  public SanctionType convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "suspensao" -> SanctionType.SUSPENSION;
      case "banimento" -> SanctionType.BAN;
      default -> throw new IllegalStateException("Valor de tipo de sanção desconhecido: " + dbData);
    };
  }
}
