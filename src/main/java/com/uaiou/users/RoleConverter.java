package com.uaiou.users;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, String> {

  @Override
  public String convertToDatabaseColumn(Role attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case MERCHANT -> "estabelecimento";
      case COURIER -> "entregador";
      case ADMIN -> "admin";
    };
  }

  @Override
  public Role convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "estabelecimento" -> Role.MERCHANT;
      case "entregador" -> Role.COURIER;
      case "admin" -> Role.ADMIN;
      default ->
          throw new IllegalStateException("Valor de tipo de usuário desconhecido: " + dbData);
    };
  }
}
