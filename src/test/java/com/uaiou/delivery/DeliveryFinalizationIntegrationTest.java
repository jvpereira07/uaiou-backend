package com.uaiou.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DeliveryCodeResponse;
import com.uaiou.orders.dto.DeliveryCompletionRequest;
import com.uaiou.orders.dto.DeliveryCompletionResponse;
import com.uaiou.orders.dto.DeliveryStateResponse;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-15.1 a RF-15.11 — critérios de aceite de T-15. */
class DeliveryFinalizationIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");
  // ~1 km do destino — dentro do raio de elegibilidade (10 km), fora do geofence (150 m).
  private static final BigDecimal LONGE_LAT = new BigDecimal("-19.918200");
  private static final BigDecimal LONGE_LNG = new BigDecimal("-43.938600");

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Critério 1: fora do geofence, sem link de completion, com a distância restante. */
  @Test
  void outsideTheGeofenceThereIsNoCompletionLinkAndDistanceIsReported() {
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LNG);
    UUID pedidoId = aceitar(courier, publicar(merchantComCredito()));

    DeliveryStateResponse estado = estadoDaEntrega(courier, pedidoId);

    assertThat(estado.geofence().inside()).isFalse();
    assertThat(estado.geofence().distanceMeters()).isGreaterThan(150.0);
    assertThat(estado.links()).doesNotContainKey("completion");
  }

  /**
   * Critério 2: dentro do geofence, link aparece; código correto finaliza (201, pedido finalizado).
   */
  @Test
  void insideTheGeofenceTheLinkAppearsAndTheCorrectCodeFinalizes() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LNG);
    UUID pedidoId = aceitar(courier, publicar(merchant));
    reportarPosicao(courier, DEST_LAT, DEST_LNG);

    DeliveryStateResponse estado = estadoDaEntrega(courier, pedidoId);
    assertThat(estado.geofence().inside()).isTrue();
    assertThat(estado.links()).containsKey("completion");

    String codigo = lerCodigo(merchant, pedidoId);
    ResponseEntity<DeliveryCompletionResponse> resposta =
        finalizar(courier, pedidoId, codigo, DEST_LAT, DEST_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resposta.getBody().completionType()).isEqualTo(FinalizationType.CODE);
    assertThat(statusNoBanco(pedidoId)).isEqualTo("finalizado");
  }

  /** Critério 3: fora do geofence, mesmo com código correto → 422 OUTSIDE_GEOFENCE. */
  @Test
  void outsideTheGeofenceEvenTheCorrectCodeFails() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LNG);
    UUID pedidoId = aceitar(courier, publicar(merchant));
    String codigo = lerCodigo(merchant, pedidoId);

    ResponseEntity<ErrorResponse> resposta =
        finalizarComErro(courier, pedidoId, codigo, LONGE_LAT, LONGE_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("OUTSIDE_GEOFENCE");
  }

  /** Critério 4: código errado → 422 e incrementa tentativa; na N-ésima, bloqueia. */
  @Test
  void wrongCodeIncrementsAttemptsAndBlocksOnTheNthFailure() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LNG);
    UUID pedidoId = aceitar(courier, publicar(merchant));

    for (int i = 0; i < 3; i++) {
      ResponseEntity<ErrorResponse> resposta =
          finalizarComErro(courier, pedidoId, "000000", DEST_LAT, DEST_LNG);
      assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
      assertThat(resposta.getBody().error().code()).isEqualTo("INVALID_DELIVERY_CODE");
    }

    Integer tentativas =
        jdbcTemplate.queryForObject(
            "select tentativas from otp where pedido_id = ?", Integer.class, pedidoId);
    String statusCodigo =
        jdbcTemplate.queryForObject(
            "select status from otp where pedido_id = ?", String.class, pedidoId);
    assertThat(tentativas).isEqualTo(3);
    assertThat(statusCodigo).isEqualTo("bloqueado");
  }

  /** Critério 5: entregador não atribuído tentando finalizar → 403. */
  @Test
  void anUnassignedCourierCannotFinalize() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LNG);
    RegisteredTestUser outro = disponivelEm(LONGE_LAT, LONGE_LNG);
    UUID pedidoId = aceitar(courier, publicar(merchant));

    ResponseEntity<ErrorResponse> resposta =
        finalizarComErro(outro, pedidoId, "000000", DEST_LAT, DEST_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resposta.getBody().error().code()).isEqualTo("NOT_ASSIGNED_COURIER");
  }

  /** Critério 6: finalizar pedido já finalizado → 409. */
  @Test
  void finalizingAnAlreadyFinalizedOrderIsAConflict() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LNG);
    UUID pedidoId = aceitar(courier, publicar(merchant));
    String codigo = lerCodigo(merchant, pedidoId);
    finalizar(courier, pedidoId, codigo, DEST_LAT, DEST_LNG);

    ResponseEntity<ErrorResponse> resposta =
        finalizarComErro(courier, pedidoId, codigo, DEST_LAT, DEST_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resposta.getBody().error().code()).isEqualTo("ORDER_ALREADY_FINALIZED");
  }

  /** Critério 7: só o estabelecimento dono lê o código; 403 pro entregador; 410 após finalizar. */
  @Test
  void onlyTheOwningMerchantReadsTheCodeAndItExpiresAfterFinalization() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LNG);
    UUID pedidoId = aceitar(courier, publicar(merchant));

    ResponseEntity<ErrorResponse> comoEntregador =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/delivery/code"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            ErrorResponse.class);
    assertThat(comoEntregador.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(comoEntregador.getBody().error().code()).isEqualTo("CODE_NOT_VISIBLE_TO_COURIER");

    ResponseEntity<DeliveryCodeResponse> primeiraLeitura = lerCodigoResponse(merchant, pedidoId);
    ResponseEntity<DeliveryCodeResponse> segundaLeitura = lerCodigoResponse(merchant, pedidoId);
    assertThat(segundaLeitura.getBody().audit().readCount()).isEqualTo(2);

    String codigo = primeiraLeitura.getBody().code();
    finalizar(courier, pedidoId, codigo, DEST_LAT, DEST_LNG);

    ResponseEntity<ErrorResponse> aposFinalizar =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/delivery/code"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(merchant).accessToken())),
            ErrorResponse.class);
    assertThat(aposFinalizar.getStatusCode()).isEqualTo(HttpStatus.GONE);
  }

  /**
   * Critério 8/10: código nunca aparece pro entregador; exatamente uma evidência; contador sobe.
   */
  @Test
  void theCodeNeverAppearsToTheCourierAndFinalizationLeavesExactlyOneEvidence() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LNG);
    UUID pedidoId = aceitar(courier, publicar(merchant));

    Integer entregasAntes = entregasRealizadas(courier.id());
    String codigo = lerCodigo(merchant, pedidoId);
    finalizar(courier, pedidoId, codigo, DEST_LAT, DEST_LNG);

    assertThat(entregasRealizadas(courier.id())).isEqualTo(entregasAntes + 1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from evidencia_entrega where pedido_id = ?",
                Integer.class,
                pedidoId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from lancamento_frete where pedido_id = ?",
                Integer.class,
                pedidoId))
        .isEqualTo(1);
  }

  private DeliveryStateResponse estadoDaEntrega(RegisteredTestUser courier, UUID pedidoId) {
    return restTemplate
        .exchange(
            baseUrl("/orders/" + pedidoId + "/delivery"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            DeliveryStateResponse.class)
        .getBody();
  }

  private String lerCodigo(RegisteredTestUser merchant, UUID pedidoId) {
    return lerCodigoResponse(merchant, pedidoId).getBody().code();
  }

  private ResponseEntity<DeliveryCodeResponse> lerCodigoResponse(
      RegisteredTestUser merchant, UUID pedidoId) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/code"),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(login(merchant).accessToken())),
        DeliveryCodeResponse.class);
  }

  private ResponseEntity<DeliveryCompletionResponse> finalizar(
      RegisteredTestUser courier, UUID pedidoId, String codigo, BigDecimal lat, BigDecimal lng) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/completion"),
        HttpMethod.POST,
        authed(
            login(courier).accessToken(),
            new DeliveryCompletionRequest(
                DeliveryCompletionRequest.Mode.CODE, codigo, null, lat, lng)),
        DeliveryCompletionResponse.class);
  }

  private ResponseEntity<ErrorResponse> finalizarComErro(
      RegisteredTestUser courier, UUID pedidoId, String codigo, BigDecimal lat, BigDecimal lng) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/completion"),
        HttpMethod.POST,
        authed(
            login(courier).accessToken(),
            new DeliveryCompletionRequest(
                DeliveryCompletionRequest.Mode.CODE, codigo, null, lat, lng)),
        ErrorResponse.class);
  }

  private String statusNoBanco(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select status from pedido where id = ?", String.class, pedidoId);
  }

  private Integer entregasRealizadas(UUID entregadorId) {
    return jdbcTemplate.queryForObject(
        "select entregas_realizadas from entregador where usuario_id = ?",
        Integer.class,
        entregadorId);
  }

  private void reportarPosicao(RegisteredTestUser courier, BigDecimal lat, BigDecimal lng) {
    restTemplate.exchange(
        baseUrl("/me/location"),
        HttpMethod.PUT,
        authed(
            login(courier).accessToken(),
            new UpdateLocationRequest(lat, lng, new BigDecimal("10.0"))),
        Void.class);
  }

  private UUID aceitar(RegisteredTestUser courier, UUID pedidoId) {
    ResponseEntity<AssignmentResponse> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/assignment"),
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            AssignmentResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    marcarColetado(pedidoId);
    return pedidoId;
  }

  private RegisteredTestUser merchantComCredito() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    return merchant;
  }

  private RegisteredTestUser disponivelEm(BigDecimal lat, BigDecimal lng) {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    restTemplate.exchange(
        baseUrl("/me/location"),
        HttpMethod.PUT,
        authed(token, new UpdateLocationRequest(lat, lng, new BigDecimal("10.0"))),
        Void.class);
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(true)),
        Object.class);
    return courier;
  }

  private UUID publicar(RegisteredTestUser merchant) {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Money.of("6.00"),
            null,
            new DestinationRequest(
                "Rua Beija-Flor",
                "45",
                null,
                "Jardim Independencia",
                "Belo Horizonte",
                DEST_LAT,
                DEST_LNG),
            new CreateOrderRequest.ReceiverRequest("Marina", "31998877665"));
    return restTemplate
        .exchange(
            baseUrl("/orders"),
            HttpMethod.POST,
            authed(login(merchant).accessToken(), request),
            OrderResponse.class)
        .getBody()
        .id();
  }
}
