package com.uaiou.shared.money;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Só aceita {@link Money} vindo de string JSON. Um número JSON (ex.: {@code "amount": 6.00}) é
 * rejeitado de propósito: aceitar os dois formatos abriria uma porta para o cliente reintroduzir
 * float na borda de entrada, justamente o que o tipo existe para impedir (RF-01.7).
 */
public class MoneyDeserializer extends ValueDeserializer<Money> {

  @Override
  public Money deserialize(JsonParser parser, DeserializationContext context)
      throws JacksonException {
    if (parser.currentToken() != JsonToken.VALUE_STRING) {
      return context.reportInputMismatch(
          Money.class,
          "Valor monetário deve ser enviado como string decimal (ex.: \"6.00\"), não como número.");
    }
    try {
      return Money.of(parser.getString());
    } catch (IllegalArgumentException e) {
      return context.reportInputMismatch(Money.class, e.getMessage());
    }
  }
}
