package com.uaiou.delivery;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class LedgerEntryTypeConverter implements AttributeConverter<LedgerEntryType, String> {

  @Override
  public String convertToDatabaseColumn(LedgerEntryType attribute) {
    return attribute == null ? null : attribute.dbValue();
  }

  @Override
  public LedgerEntryType convertToEntityAttribute(String dbData) {
    return dbData == null ? null : LedgerEntryType.fromDbValue(dbData);
  }
}
