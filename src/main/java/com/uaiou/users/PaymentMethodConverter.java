package com.uaiou.users;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentMethodConverter implements AttributeConverter<PaymentMethod, String> {

  @Override
  public String convertToDatabaseColumn(PaymentMethod attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case CASH -> "dinheiro";
      case CREDIT -> "credito";
      case DEBIT -> "debito";
      case PIX -> "pix";
    };
  }

  @Override
  public PaymentMethod convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "dinheiro" -> PaymentMethod.CASH;
      case "credito" -> PaymentMethod.CREDIT;
      case "debito" -> PaymentMethod.DEBIT;
      case "pix" -> PaymentMethod.PIX;
      default ->
          throw new IllegalStateException("Valor de forma de pagamento desconhecido: " + dbData);
    };
  }
}
