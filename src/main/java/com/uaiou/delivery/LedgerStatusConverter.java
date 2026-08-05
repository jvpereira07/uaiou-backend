package com.uaiou.delivery;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LedgerStatusConverter implements AttributeConverter<LedgerStatus, String> {

  @Override
  public String convertToDatabaseColumn(LedgerStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return attribute == LedgerStatus.RECEIVABLE ? "a_receber" : "acertado";
  }

  @Override
  public LedgerStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "a_receber" -> LedgerStatus.RECEIVABLE;
      case "acertado" -> LedgerStatus.SETTLED;
      default -> throw new IllegalStateException("Status de lançamento desconhecido: " + dbData);
    };
  }
}
