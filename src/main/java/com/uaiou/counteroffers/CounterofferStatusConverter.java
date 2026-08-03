package com.uaiou.counteroffers;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class CounterofferStatusConverter implements AttributeConverter<CounterofferStatus, String> {

  @Override
  public String convertToDatabaseColumn(CounterofferStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case PENDING -> "pendente";
      case ACCEPTED -> "aceita";
      case REJECTED -> "recusada";
      case INVALIDATED -> "invalidada";
    };
  }

  @Override
  public CounterofferStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "pendente" -> CounterofferStatus.PENDING;
      case "aceita" -> CounterofferStatus.ACCEPTED;
      case "recusada" -> CounterofferStatus.REJECTED;
      case "invalidada" -> CounterofferStatus.INVALIDATED;
      default -> throw new IllegalStateException("Status de contraoferta desconhecido: " + dbData);
    };
  }
}
