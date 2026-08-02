package com.uaiou.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RF-04.5 — critério de aceite 7 de T-04. O banco não expressa a invariante do CTI (T-02); só um
 * `usuario` corrompido manualmente (aqui, via SQL direto — a aplicação nunca deixaria isso
 * acontecer sozinha) consegue simular o cenário.
 */
class ProfileCtiInvariantIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void usuarioWithoutTheMatchingChildRowFailsLoudInsteadOfReturningAPartialProfile() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse session = login(user);

    jdbcTemplate.update("delete from entregador where usuario_id = ?", user.id());

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(session.accessToken());
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/me"), HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
  }
}
