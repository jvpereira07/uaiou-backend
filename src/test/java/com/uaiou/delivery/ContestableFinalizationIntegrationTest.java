package com.uaiou.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.delivery.service.ContestableConsolidationJob;
import com.uaiou.delivery.service.ContingencyExpiryJob;
import com.uaiou.notifications.dto.NotificationListResponse;
import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CodeRecoveryRequest;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DeliveryCompletionRequest;
import com.uaiou.orders.dto.DeliveryCompletionResponse;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import com.uaiou.uploads.Purpose;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-17.1 a RF-17.9 — critérios de aceite de T-17. */
class ContestableFinalizationIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");
  private static final BigDecimal PERTO_LAT = new BigDecimal("-19.918200");
  private static final BigDecimal PERTO_LNG = new BigDecimal("-43.938600");

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ContingencyExpiryJob contingencyExpiryJob;
  @Autowired private ContestableConsolidationJob contestableConsolidationJob;

  /** Critério 1: sem liberação, modo contestável → 403 CONTESTABLE_NOT_RELEASED. */
  @Test
  void withoutReleaseTheContestableModeIsForbidden() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicar(merchantComCredito()));
    UUID uploadId = createAndConfirmUpload(login(courier).accessToken(), Purpose.DELIVERY_PROOF);

    ResponseEntity<ErrorResponse> resposta =
        finalizarContestavelComErro(courier, pedidoId, uploadId, DEST_LAT, DEST_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resposta.getBody().error().code()).isEqualTo("CONTESTABLE_NOT_RELEASED");
  }

  /** Critério 2: liberado, sem foto → 422. */
  @Test
  void releasedButWithoutProofFails() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = liberarContestavel(courier, publicarSemTelefone(merchantComCredito()));

    ResponseEntity<ErrorResponse> resposta =
        finalizarContestavelComErro(courier, pedidoId, null, DEST_LAT, DEST_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("MISSING_PROOF");
  }

  /** Critério 3: liberado, foto válida, dentro do geofence → 201, finalizado_contestavel. */
  @Test
  void releasedWithProofAndInsideTheGeofenceSucceeds() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = liberarContestavel(courier, publicarSemTelefone(merchantComCredito()));
    UUID uploadId = createAndConfirmUpload(login(courier).accessToken(), Purpose.DELIVERY_PROOF);

    ResponseEntity<DeliveryCompletionResponse> resposta =
        finalizarContestavel(courier, pedidoId, uploadId, DEST_LAT, DEST_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resposta.getBody().completionType()).isEqualTo(FinalizationType.CONTESTABLE);
    assertThat(statusNoBanco(pedidoId)).isEqualTo("finalizado_contestavel");
  }

  /** Critério 4: fora do geofence, mesmo liberado e com foto → 422. */
  @Test
  void outsideTheGeofenceFailsEvenWhenReleased() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = liberarContestavel(courier, publicarSemTelefone(merchantComCredito()));
    UUID uploadId = createAndConfirmUpload(login(courier).accessToken(), Purpose.DELIVERY_PROOF);

    ResponseEntity<ErrorResponse> resposta =
        finalizarContestavelComErro(courier, pedidoId, uploadId, PERTO_LAT, PERTO_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("OUTSIDE_GEOFENCE");
  }

  /** Critério 5: upload já vinculado a outra entrega → 409 (mesmo dono, segunda entrega). */
  @Test
  void anAlreadyLinkedUploadIsAConflict() {
    RegisteredTestUser courier = disponivel();
    UUID primeiroPedido = liberarContestavel(courier, publicarSemTelefone(merchantComCredito()));
    UUID uploadId = createAndConfirmUpload(login(courier).accessToken(), Purpose.DELIVERY_PROOF);
    finalizarContestavel(courier, primeiroPedido, uploadId, DEST_LAT, DEST_LNG);

    UUID segundoPedido = liberarContestavel(courier, publicarSemTelefone(merchantComCredito()));

    ResponseEntity<ErrorResponse> resposta =
        finalizarContestavelComErro(courier, segundoPedido, uploadId, DEST_LAT, DEST_LNG);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resposta.getBody().error().code()).isEqualTo("UPLOAD_ALREADY_LINKED");
  }

  /** Critério 6: lançamento nasce a_receber, igual à finalização normal. */
  @Test
  void createsAReceivableLedgerEntryJustLikeNormalFinalization() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = liberarContestavel(courier, publicarSemTelefone(merchantComCredito()));
    UUID uploadId = createAndConfirmUpload(login(courier).accessToken(), Purpose.DELIVERY_PROOF);

    finalizarContestavel(courier, pedidoId, uploadId, DEST_LAT, DEST_LNG);

    String status =
        jdbcTemplate.queryForObject(
            "select status from lancamento_frete where pedido_id = ?", String.class, pedidoId);
    assertThat(status).isEqualTo("a_receber");
  }

  /** Critério 7: janela vencida sem contestação → job move para finalizado. */
  @Test
  void expiredWindowWithoutContestConsolidatesToFinalized() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = liberarContestavel(courier, publicarSemTelefone(merchantComCredito()));
    UUID uploadId = createAndConfirmUpload(login(courier).accessToken(), Purpose.DELIVERY_PROOF);
    finalizarContestavel(courier, pedidoId, uploadId, DEST_LAT, DEST_LNG);

    jdbcTemplate.update(
        "update pedido set finalizado_em = ? where id = ?",
        java.sql.Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)),
        pedidoId);

    contestableConsolidationJob.consolidarJanelasVencidas();

    assertThat(statusNoBanco(pedidoId)).isEqualTo("finalizado");
  }

  /** Critério 8: estabelecimento recebe delivery.contestable; nenhuma rota de disputa existe. */
  @Test
  void theMerchantIsNotifiedAndThereIsNoDisputeRoute() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = liberarContestavel(courier, publicarSemTelefone(merchant));
    UUID uploadId = createAndConfirmUpload(login(courier).accessToken(), Purpose.DELIVERY_PROOF);

    finalizarContestavel(courier, pedidoId, uploadId, DEST_LAT, DEST_LNG);

    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              NotificationListResponse inbox =
                  restTemplate
                      .exchange(
                          baseUrl("/me/notifications?type=delivery.contestable&perPage=10"),
                          HttpMethod.GET,
                          new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                          NotificationListResponse.class)
                      .getBody();
              assertThat(inbox.data())
                  .anyMatch(n -> pedidoId.toString().equals(n.payload().get("orderId")));
            });

    ResponseEntity<String> disputa =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/dispute"),
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(login(merchant).accessToken())),
            String.class);
    assertThat(disputa.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.METHOD_NOT_ALLOWED);
  }

  private ResponseEntity<DeliveryCompletionResponse> finalizarContestavel(
      RegisteredTestUser courier, UUID pedidoId, UUID uploadId, BigDecimal lat, BigDecimal lng) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/completion"),
        HttpMethod.POST,
        authed(
            login(courier).accessToken(),
            new DeliveryCompletionRequest(
                DeliveryCompletionRequest.Mode.CONTESTABLE, null, uploadId, lat, lng)),
        DeliveryCompletionResponse.class);
  }

  private ResponseEntity<ErrorResponse> finalizarContestavelComErro(
      RegisteredTestUser courier, UUID pedidoId, UUID uploadId, BigDecimal lat, BigDecimal lng) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/completion"),
        HttpMethod.POST,
        authed(
            login(courier).accessToken(),
            new DeliveryCompletionRequest(
                DeliveryCompletionRequest.Mode.CONTESTABLE, null, uploadId, lat, lng)),
        ErrorResponse.class);
  }

  /** Percorre a escada inteira (T-16) até o modo contestável ficar liberado pelo servidor. */
  private UUID liberarContestavel(RegisteredTestUser courier, UUID pedidoId) {
    aceitar(courier, pedidoId);
    restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/code-recoveries"),
        HttpMethod.POST,
        authed(login(courier).accessToken(), new CodeRecoveryRequest("receiver_without_code")),
        Object.class);
    jdbcTemplate.update(
        "update contingencia_otp set prazo_em = ? where pedido_id = ? and degrau = 2 and resultado = 'notificado'",
        java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        pedidoId);
    contingencyExpiryJob.resolverPrazosVencidos();
    return pedidoId;
  }

  private String statusNoBanco(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select status from pedido where id = ?", String.class, pedidoId);
  }

  private UUID aceitar(RegisteredTestUser courier, UUID pedidoId) {
    ResponseEntity<AssignmentResponse> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/assignment"),
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            AssignmentResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return pedidoId;
  }

  private RegisteredTestUser merchantComCredito() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    return merchant;
  }

  private RegisteredTestUser disponivel() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    restTemplate.exchange(
        baseUrl("/me/location"),
        HttpMethod.PUT,
        authed(token, new UpdateLocationRequest(DEST_LAT, DEST_LNG, new BigDecimal("10.0"))),
        Void.class);
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(true)),
        Object.class);
    return courier;
  }

  private UUID publicar(RegisteredTestUser merchant) {
    return publicarComTelefone(merchant, "31998877665");
  }

  private UUID publicarSemTelefone(RegisteredTestUser merchant) {
    return publicarComTelefone(merchant, null);
  }

  private UUID publicarComTelefone(RegisteredTestUser merchant, String telefone) {
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
            new CreateOrderRequest.ReceiverRequest("Marina", telefone));
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
