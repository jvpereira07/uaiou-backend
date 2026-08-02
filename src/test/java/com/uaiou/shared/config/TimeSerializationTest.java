package com.uaiou.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Critério de aceite 8: data serializada sai em ISO-8601 UTC independentemente do fuso da máquina.
 * A prova real não é confiar em {@code TZ=UTC} no ambiente — é usar {@link Instant} (que não tem
 * fuso: é sempre UTC por definição) para qualquer timestamp, o que torna a serialização correta
 * mesmo que o processo rode num host com outro fuso padrão. Este teste muda o fuso padrão da JVM
 * deliberadamente para confirmar isso.
 *
 * <p>Jackson 3 (o que o Spring Boot 4 usa) já serializa {@link Instant} como ISO-8601 UTC por
 * padrão, sem precisar de módulo ou feature adicional — confirmado empiricamente ao escrever este
 * teste.
 */
class TimeSerializationTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();

  private TimeZone originalDefault;

  @BeforeEach
  void captureOriginalTimeZone() {
    originalDefault = TimeZone.getDefault();
  }

  @AfterEach
  void restoreOriginalTimeZone() {
    TimeZone.setDefault(originalDefault);
  }

  private record Wrapper(Instant createdAt) {}

  @Test
  void instantSerializesAsUtcRegardlessOfHostDefaultTimeZone() {
    TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    Instant fixedInstant = Instant.parse("2026-07-24T18:30:00Z");

    String json = jsonMapper.writeValueAsString(new Wrapper(fixedInstant));

    assertThat(json).isEqualTo("{\"createdAt\":\"2026-07-24T18:30:00Z\"}");
  }

  @Test
  void resultIsIdenticalUnderADifferentHostTimeZoneToo() {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
    Instant fixedInstant = Instant.parse("2026-07-24T18:30:00Z");

    String json = jsonMapper.writeValueAsString(new Wrapper(fixedInstant));

    assertThat(json).isEqualTo("{\"createdAt\":\"2026-07-24T18:30:00Z\"}");
  }
}
