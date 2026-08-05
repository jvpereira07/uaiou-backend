package com.uaiou.delivery;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DeliveryCodeStatusConverter implements AttributeConverter<DeliveryCodeStatus, String> {

  @Override
  public String convertToDatabaseColumn(DeliveryCodeStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case ISSUED -> "gerado";
      case VALIDATED -> "validado";
      case EXPIRED -> "expirado";
      case BLOCKED -> "bloqueado";
    };
  }

  @Override
  public DeliveryCodeStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "gerado" -> DeliveryCodeStatus.ISSUED;
      case "validado" -> DeliveryCodeStatus.VALIDATED;
      case "expirado" -> DeliveryCodeStatus.EXPIRED;
      case "bloqueado" -> DeliveryCodeStatus.BLOCKED;
      default -> throw new IllegalStateException("Status de código desconhecido: " + dbData);
    };
  }
}
