package com.uaiou.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prova o {@link GlobalExceptionHandler} de ponta a ponta via HTTP real. As rotas {@code /_test/*}
 * existem só neste contexto de teste (importadas explicitamente, nunca no component scan da
 * aplicação) — nenhuma rota de demonstração vaza para a API de produção (critério de aceite 4).
 */
@Import(ErrorHandlingIntegrationTest.TestOnlyController.class)
class ErrorHandlingIntegrationTest extends AbstractIntegrationTest {

  @Test
  void unmatchedRouteRespondsWithStandardErrorEnvelope() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.getForEntity(baseUrl("/rota-que-nao-existe"), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("NOT_FOUND");
  }

  @Test
  void unhandledExceptionRespondsWithInternalErrorAndNoStackTrace() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.getForEntity(baseUrl("/_test/boom"), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    ErrorBody error = response.getBody().error();
    assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
    assertThat(error.details()).containsKey("requestId");
    assertThat(response.getHeaders().getFirst("Content-Type")).contains("application/json");
    // nenhum frame de stack trace no corpo — apenas os quatro campos do envelope
    assertThat(error.message()).doesNotContain(".java:");
  }

  @Test
  void businessRuleExceptionCarriesRuleAndDetails() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.getForEntity(baseUrl("/_test/business-rule"), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    ErrorBody error = response.getBody().error();
    assertThat(error.code()).isEqualTo("INSUFFICIENT_CREDITS");
    assertThat(error.rule()).isEqualTo("RN-05.1");
    assertThat(error.details()).containsEntry("required", 1);
  }

  @RestController
  public static class TestOnlyController {

    @GetMapping("/_test/boom")
    public String boom() {
      throw new RuntimeException("erro proposital, só para provar o handler de exceção genérico");
    }

    @GetMapping("/_test/business-rule")
    public String businessRule() {
      throw new BusinessRuleException(
          "INSUFFICIENT_CREDITS",
          "Créditos insuficientes para publicar o pedido.",
          "RN-05.1",
          Map.of("required", 1, "available", 0));
    }
  }
}
