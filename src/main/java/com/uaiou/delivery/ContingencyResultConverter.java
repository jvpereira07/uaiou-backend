package com.uaiou.delivery;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContingencyResultConverter implements AttributeConverter<ContingencyResult, String> {

  @Override
  public String convertToDatabaseColumn(ContingencyResult attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case RESENT -> "reenviado";
      case NOTIFIED -> "notificado";
      case DISPATCHED -> "repassado";
      case NO_PHONE -> "sem_telefone";
      case NO_RESPONSE -> "sem_resposta";
      case EXPIRED -> "expirado";
    };
  }

  @Override
  public ContingencyResult convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "reenviado" -> ContingencyResult.RESENT;
      case "notificado" -> ContingencyResult.NOTIFIED;
      case "repassado" -> ContingencyResult.DISPATCHED;
      case "sem_telefone" -> ContingencyResult.NO_PHONE;
      case "sem_resposta" -> ContingencyResult.NO_RESPONSE;
      case "expirado" -> ContingencyResult.EXPIRED;
      default ->
          throw new IllegalStateException("Resultado de contingência desconhecido: " + dbData);
    };
  }
}
