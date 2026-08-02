package com.uaiou.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Constrói o {@code ObjectMapper} (Jackson 3 — {@code tools.jackson.*}, o que o Spring Boot 4
 * realmente usa) manualmente com só o {@link MoneyModule} registrado. Não depende de subir contexto
 * Spring para provar a serialização, o que torna o teste determinístico e rápido — mas é exatamente
 * a mesma configuração que {@link MoneyModule} contribui automaticamente ao mapper real da
 * aplicação.
 */
class MoneyJsonTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().addModule(new MoneyModule()).build();

  private record MoneyHolder(Money amount) {}

  @Test
  void serializesAsDecimalStringNeverAsJsonNumber() {
    String json = jsonMapper.writeValueAsString(new MoneyHolder(Money.of("6.00")));

    assertThat(json).isEqualTo("{\"amount\":\"6.00\"}");
  }

  @Test
  void deserializesFromDecimalString() {
    MoneyHolder holder = jsonMapper.readValue("{\"amount\":\"6.00\"}", MoneyHolder.class);

    assertThat(holder.amount()).isEqualTo(Money.of("6.00"));
  }

  @Test
  void rejectsAJsonNumberOnTheWayIn() {
    assertThatThrownBy(() -> jsonMapper.readValue("{\"amount\":6.00}", MoneyHolder.class))
        .isInstanceOf(DatabindException.class);
  }

  @Test
  void fullRoundTripNeverIntroducesFloatingPointError() {
    record TwoAmounts(Money a, Money b) {}
    TwoAmounts parsed = jsonMapper.readValue("{\"a\":\"0.10\",\"b\":\"0.20\"}", TwoAmounts.class);

    Money sum = parsed.a().add(parsed.b());
    String json = jsonMapper.writeValueAsString(new MoneyHolder(sum));

    assertThat(json).isEqualTo("{\"amount\":\"0.30\"}");
  }
}
