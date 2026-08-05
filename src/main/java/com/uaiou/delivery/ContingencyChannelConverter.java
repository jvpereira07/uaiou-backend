package com.uaiou.delivery;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContingencyChannelConverter implements AttributeConverter<ContingencyChannel, String> {

  @Override
  public String convertToDatabaseColumn(ContingencyChannel attribute) {
    if (attribute == null) {
      return null;
    }
    return attribute == ContingencyChannel.SMS ? "sms" : "estabelecimento";
  }

  @Override
  public ContingencyChannel convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "sms" -> ContingencyChannel.SMS;
      case "estabelecimento" -> ContingencyChannel.MERCHANT;
      default -> throw new IllegalStateException("Canal de contingência desconhecido: " + dbData);
    };
  }
}
