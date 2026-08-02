package com.uaiou.shared.money;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Sempre grava {@link Money} como string decimal (ex.: {@code "6.00"}) — nunca como número JSON.
 */
public class MoneySerializer extends ValueSerializer<Money> {

  @Override
  public void serialize(Money value, JsonGenerator gen, SerializationContext context)
      throws JacksonException {
    gen.writeString(value.toString());
  }
}
