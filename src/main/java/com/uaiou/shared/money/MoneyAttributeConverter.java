package com.uaiou.shared.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/**
 * Aplica-se automaticamente a todo campo de entidade JPA do tipo {@link Money} ({@code autoApply =
 * true}), sem precisar de {@code @Convert} em cada campo. A coluna correspondente é {@code
 * numeric(12,2)} — definida pelas migrations (T-02), não por este conversor.
 */
@Converter(autoApply = true)
public class MoneyAttributeConverter implements AttributeConverter<Money, BigDecimal> {

  @Override
  public BigDecimal convertToDatabaseColumn(Money attribute) {
    return attribute == null ? null : attribute.amount();
  }

  @Override
  public Money convertToEntityAttribute(BigDecimal dbData) {
    return dbData == null ? null : Money.of(dbData);
  }
}
