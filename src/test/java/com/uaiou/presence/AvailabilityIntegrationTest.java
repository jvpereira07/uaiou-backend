package com.uaiou.presence;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.presence.dto.AvailabilityResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-10.1 a RF-10.4 — critérios de aceite 1, 3, 4 e 8 de T-10. */
class AvailabilityIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private EntregadorRepository entregadorRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void goingAvailableWithoutAnyPositionIsRejected() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();

    ResponseEntity<ErrorResponse> response = setAvailabilityExpectingError(token, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("LOCATION_REQUIRED");
    assertThat(response.getBody().error().rule()).isEqualTo("RN-01.1");
  }

  @Test
  void goingAvailableWithAStalePositionIsRejected() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    reportLocation(token);
    // Envelhece a posição além do limite de frescor (5 min por padrão) — a rota não deixa gravar
    // uma
    // posição no passado, então o carimbo vai direto no banco.
    jdbcTemplate.update(
        "update entregador set localizacao_em = ? where usuario_id = ?",
        java.sql.Timestamp.from(Instant.now().minus(30, ChronoUnit.MINUTES)),
        courier.id());

    ResponseEntity<ErrorResponse> response = setAvailabilityExpectingError(token, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("LOCATION_REQUIRED");
  }

  @Test
  void goingAvailableWithAFreshPositionSucceedsAndReturnsSince() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    reportLocation(token);

    ResponseEntity<AvailabilityResponse> response = setAvailability(token, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().available()).isTrue();
    assertThat(response.getBody().since()).isNotNull();
    assertThat(response.getBody().links()).containsKey("openOrders");
    assertThat(entregadorRepository.findById(courier.id()).orElseThrow().isDisponivel()).isTrue();
  }

  @Test
  void goingUnavailableClearsTheSinceMarkAndDoesNotTouchAcceptedOrders() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    reportLocation(token);
    setAvailability(token, true);
    // RF-10.3: um pedido já aceito por este entregador não pode ser afetado por desligar.
    java.util.UUID pedidoId = createAcceptedPedido(courier.id());

    ResponseEntity<AvailabilityResponse> response = setAvailability(token, false);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().available()).isFalse();
    assertThat(response.getBody().since()).isNull();

    Entregador entregador = entregadorRepository.findById(courier.id()).orElseThrow();
    assertThat(entregador.isDisponivel()).isFalse();
    assertThat(entregador.getDisponivelDesde()).isNull();

    String status =
        jdbcTemplate.queryForObject(
            "select status from pedido where id = ?", String.class, pedidoId);
    assertThat(status).isEqualTo("aceito");
  }

  @Test
  void repeatedActivationKeepsTheOriginalSince() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    reportLocation(token);
    Instant first = setAvailability(token, true).getBody().since();

    Instant second = setAvailability(token, true).getBody().since();

    // O relógio de horas disponíveis (RF-10.10) não pode reiniciar a cada heartbeat do app.
    assertThat(second).isEqualTo(first);
  }

  @Test
  void pendingCourierCannotGoAvailable() {
    // Sem ativar: registerCourier deixa o usuário 'pendente'.
    RegisteredTestUser courier = registerCourier();
    String token = login(courier).accessToken();

    ResponseEntity<ErrorResponse> response = setAvailabilityExpectingError(token, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("ACCOUNT_NOT_ACTIVE");
  }

  @Test
  void suspendedCourierCannotGoAvailable() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    reportLocation(token);
    moderation.suspend(courier.id(), "Suspenso no teste", Instant.now().plus(1, ChronoUnit.DAYS));

    ResponseEntity<ErrorResponse> response = setAvailabilityExpectingError(token, true);

    // O middleware de escrita (RF-03.9) barra antes mesmo de chegar ao serviço — o importante é o
    // 403.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void merchantCannotUseCourierPresenceRoutes() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    String token = login(merchant).accessToken();

    ResponseEntity<ErrorResponse> response = setAvailabilityExpectingError(token, true);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("COURIER_ONLY");
  }

  private void reportLocation(String token) {
    ResponseEntity<Void> response =
        restTemplate.exchange(
            baseUrl("/me/location"),
            HttpMethod.PUT,
            authed(
                token,
                new UpdateLocationRequest(
                    new BigDecimal("-19.918200"),
                    new BigDecimal("-43.938600"),
                    new BigDecimal("12.5"))),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private ResponseEntity<AvailabilityResponse> setAvailability(String token, boolean available) {
    return restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(available)),
        AvailabilityResponse.class);
  }

  private ResponseEntity<ErrorResponse> setAvailabilityExpectingError(
      String token, boolean available) {
    return restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(available)),
        ErrorResponse.class);
  }

  /** Pedido 'aceito' mínimo válido — a rota real de aceite é do T-13. */
  private java.util.UUID createAcceptedPedido(java.util.UUID entregadorId) {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    java.util.UUID id = com.uaiou.shared.id.UuidV7.next();
    jdbcTemplate.update(
        "insert into pedido (id, numero, estabelecimento_id, entregador_id, status, frete_proposto,"
            + " frete_final, creditos_consumidos, dest_bairro, dest_rua, dest_numero, dest_lat,"
            + " dest_long, recebedor_nome, aceito_em) values (?, ?, ?, ?, 'aceito', ?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?, now())",
        id,
        "P-" + java.util.UUID.randomUUID().toString().substring(0, 8),
        merchant.id(),
        entregadorId,
        new BigDecimal("6.00"),
        new BigDecimal("6.00"),
        1,
        "Centro",
        "Rua de Teste",
        "100",
        new BigDecimal("-19.917299"),
        new BigDecimal("-43.934559"),
        "Cliente de Teste");
    return id;
  }
}
