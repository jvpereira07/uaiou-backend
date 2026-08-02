package com.uaiou.users;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DocumentApprovalStatusConverter
    implements AttributeConverter<DocumentApprovalStatus, String> {

  @Override
  public String convertToDatabaseColumn(DocumentApprovalStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case PENDING -> "pendente";
      case APPROVED -> "aprovado";
      case REJECTED -> "rejeitado";
      case SUPERSEDED -> "superado";
    };
  }

  @Override
  public DocumentApprovalStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "pendente" -> DocumentApprovalStatus.PENDING;
      case "aprovado" -> DocumentApprovalStatus.APPROVED;
      case "rejeitado" -> DocumentApprovalStatus.REJECTED;
      case "superado" -> DocumentApprovalStatus.SUPERSEDED;
      default ->
          throw new IllegalStateException(
              "Valor de status de aprovação de documento desconhecido: " + dbData);
    };
  }
}
