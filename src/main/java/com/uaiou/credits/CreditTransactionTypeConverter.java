package com.uaiou.credits;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CreditTransactionTypeConverter
    implements AttributeConverter<CreditTransactionType, String> {

  @Override
  public String convertToDatabaseColumn(CreditTransactionType attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case MONTHLY_QUOTA -> "cota_mensal";
      case POSTING_CONSUMPTION -> "consumo_postagem";
      case ADJUSTMENT -> "ajuste";
    };
  }

  @Override
  public CreditTransactionType convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "cota_mensal" -> CreditTransactionType.MONTHLY_QUOTA;
      case "consumo_postagem" -> CreditTransactionType.POSTING_CONSUMPTION;
      case "ajuste" -> CreditTransactionType.ADJUSTMENT;
      default ->
          throw new IllegalStateException(
              "Valor de tipo de transação de crédito desconhecido: " + dbData);
    };
  }
}
