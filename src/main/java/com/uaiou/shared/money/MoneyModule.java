package com.uaiou.shared.money;

import org.springframework.stereotype.Component;
import tools.jackson.databind.module.SimpleModule;

/**
 * Registrado automaticamente pela autoconfiguração do Jackson do Spring Boot — todo bean {@code
 * tools.jackson.databind.JacksonModule} no contexto é descoberto sem configuração adicional.
 *
 * <p>Spring Boot 4 usa Jackson 3 ({@code tools.jackson.*}) na aplicação, não Jackson 2 ({@code
 * com.fasterxml.jackson.databind.*}) — {@link SimpleModule} aqui é o tipo de {@code tools.jackson}.
 */
@Component
public class MoneyModule extends SimpleModule {

  public MoneyModule() {
    super("MoneyModule");
    addSerializer(Money.class, new MoneySerializer());
    addDeserializer(Money.class, new MoneyDeserializer());
  }
}
