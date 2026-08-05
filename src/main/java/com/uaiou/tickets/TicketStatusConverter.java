package com.uaiou.tickets;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TicketStatusConverter implements AttributeConverter<TicketStatus, String> {

  @Override
  public String convertToDatabaseColumn(TicketStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case OPEN -> "aberto";
      case IN_PROGRESS -> "em_atendimento";
      case RESOLVED -> "resolvido";
    };
  }

  @Override
  public TicketStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "aberto" -> TicketStatus.OPEN;
      case "em_atendimento" -> TicketStatus.IN_PROGRESS;
      case "resolvido" -> TicketStatus.RESOLVED;
      default -> throw new IllegalStateException("Status de chamado desconhecido: " + dbData);
    };
  }
}
