package com.uaiou.shared.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.shared.web.CorrelationIdFilter;
import com.uaiou.support.AbstractIntegrationTest;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prova, com um Postgres real (não H2 — RF-01.13), que a cadeia inteira da fundação funciona junta:
 * Flyway migra o schema, a aplicação sobe, o healthcheck consulta o banco de verdade, o
 * identificador de correlação viaja no header e aparece no log estruturado daquela mesma requisição
 * (RF-01.8/01.9, critérios de aceite 2 e 5).
 */
@ExtendWith(OutputCaptureExtension.class)
class HealthEndpointIntegrationTest extends AbstractIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void respondsUpWithVersionAndDatabaseState() {
    ResponseEntity<HealthStatus> response =
        restTemplate.getForEntity(baseUrl("/health"), HealthStatus.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("UP");
    assertThat(response.getBody().database()).isEqualTo("UP");
    assertThat(response.getBody().version()).isNotBlank();
  }

  @Test
  void flywayActuallyRanAgainstTheRealDatabase() throws SQLException {
    Integer historyTableCount =
        jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'flyway_schema_history'",
            Integer.class);

    assertThat(historyTableCount).isEqualTo(1);
  }

  @Test
  void correlationIdRoundTripsFromHeaderToResponseAndToTheLogOfThatRequest(CapturedOutput output) {
    String requestId = UUID.randomUUID().toString();
    HttpHeaders headers = new HttpHeaders();
    headers.add(CorrelationIdFilter.HEADER_NAME, requestId);

    ResponseEntity<HealthStatus> response =
        restTemplate.exchange(
            baseUrl("/health"), HttpMethod.GET, new HttpEntity<>(headers), HealthStatus.class);

    assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME))
        .isEqualTo(requestId);
    assertThat(output.getOut()).contains(requestId);
  }

  @Test
  void generatesARequestIdWhenTheClientDoesNotSendOne() {
    ResponseEntity<HealthStatus> response =
        restTemplate.getForEntity(baseUrl("/health"), HealthStatus.class);

    String generatedId = response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME);
    assertThat(generatedId).isNotBlank();
    assertThat(UUID.fromString(generatedId)).isNotNull();
  }
}
