package com.uaiou.credits;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SubscriptionStatusConverter implements AttributeConverter<SubscriptionStatus, String> {

  @Override
  public String convertToDatabaseColumn(SubscriptionStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case ACTIVE -> "ativa";
      case CANCELLED -> "cancelada";
      case PAST_DUE -> "inadimplente";
    };
  }

  @Override
  public SubscriptionStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "ativa" -> SubscriptionStatus.ACTIVE;
      case "cancelada" -> SubscriptionStatus.CANCELLED;
      case "inadimplente" -> SubscriptionStatus.PAST_DUE;
      default ->
          throw new IllegalStateException("Valor de status de assinatura desconhecido: " + dbData);
    };
  }
}
