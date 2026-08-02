package com.uaiou.uploads;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PurposeConverter implements AttributeConverter<Purpose, String> {

  @Override
  public String convertToDatabaseColumn(Purpose attribute) {
    if (attribute == null) {
      return null;
    }
    return switch (attribute) {
      case IDENTITY_DOCUMENT -> "documento_identidade";
      case DRIVER_LICENSE -> "cnh";
      case VEHICLE_DOCUMENT -> "documento_veiculo";
      case MERCHANT_LOGO -> "logo_estabelecimento";
      case DELIVERY_PROOF -> "comprovante_entrega";
      case CNPJ_DOCUMENT -> "documento_cnpj";
    };
  }

  @Override
  public Purpose convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    return switch (dbData) {
      case "documento_identidade" -> Purpose.IDENTITY_DOCUMENT;
      case "cnh" -> Purpose.DRIVER_LICENSE;
      case "documento_veiculo" -> Purpose.VEHICLE_DOCUMENT;
      case "logo_estabelecimento" -> Purpose.MERCHANT_LOGO;
      case "comprovante_entrega" -> Purpose.DELIVERY_PROOF;
      case "documento_cnpj" -> Purpose.CNPJ_DOCUMENT;
      default ->
          throw new IllegalStateException("Valor de purpose de upload desconhecido: " + dbData);
    };
  }
}
