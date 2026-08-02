package com.uaiou.users;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

  @Override
  public String convertToDatabaseColumn(UserStatus attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case PENDING -> "pendente";
      case ACTIVE -> "ativo";
      case SUSPENDED -> "suspenso";
      case BANNED -> "banido";
      case REJECTED -> "rejeitado";
    };
  }

  @Override
  public UserStatus convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "pendente" -> UserStatus.PENDING;
      case "ativo" -> UserStatus.ACTIVE;
      case "suspenso" -> UserStatus.SUSPENDED;
      case "banido" -> UserStatus.BANNED;
      case "rejeitado" -> UserStatus.REJECTED;
      default ->
          throw new IllegalStateException("Valor de status de usuário desconhecido: " + dbData);
    };
  }
}
