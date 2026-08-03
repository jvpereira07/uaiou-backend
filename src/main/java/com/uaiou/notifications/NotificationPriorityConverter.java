package com.uaiou.notifications;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationPriorityConverter
    implements AttributeConverter<NotificationPriority, String> {

  @Override
  public String convertToDatabaseColumn(NotificationPriority attribute) {
    if (attribute == null) {
      return null;
    }
    return attribute == NotificationPriority.URGENT ? "urgente" : "normal";
  }

  @Override
  public NotificationPriority convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "urgente" -> NotificationPriority.URGENT;
      case "normal" -> NotificationPriority.NORMAL;
      default -> throw new IllegalStateException("Prioridade desconhecida: " + dbData);
    };
  }
}
