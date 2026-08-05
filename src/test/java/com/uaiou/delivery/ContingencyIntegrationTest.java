package com.uaiou.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.delivery.service.ContingencyExpiryJob;
import com.uaiou.notifications.dto.NotificationListResponse;
import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CodeDispatchRequest;
import com.uaiou.orders.dto.CodeDispatchResponse;
import com.uaiou.orders.dto.CodeRecoveryRequest;
import com.uaiou.orders.dto.CodeRecoveryResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
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

/** RF-16.1 a RF-16.9 — critérios de aceite de T-16. */
class ContingencyIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");
  private static final BigDecimal PERTO_LAT = new BigDecimal("-19.918200");
  private static final BigDecimal PERTO_LNG = new BigDecimal("-43.938600");

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ContingencyExpiryJob contingencyExpiryJob;

  /** Critério 1: com telefone, degrau 1 reenvia SMS com o mesmo código. */
  @Test
  void withPhoneTheFirstStepResendsTheSameCode() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicar(merchant));
    String codigoAntes = lerCodigo(merchant, pedidoId);

    ResponseEntity<CodeRecoveryResponse> resposta = acionar(courier, pedidoId);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resposta.getBody().step()).isEqualTo(1);
    assertThat(lerCodigo(merchant, pedidoId)).isEqualTo(codigoAntes);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from contingencia_otp where pedido_id = ? and degrau = 1",
                Integer.class,
                pedidoId))
        .isEqualTo(1);
  }

  /** Critério 2: sem telefone, vai direto ao degrau 2 e devolve prazo. */
  @Test
  void withoutPhoneItGoesStraightToStepTwo() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicarSemTelefone(merchant));

    ResponseEntity<CodeRecoveryResponse> resposta = acionar(courier, pedidoId);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resposta.getBody().step()).isEqualTo(2);
    assertThat(resposta.getBody().contestableReleased()).isFalse();
    assertThat(resposta.getBody().merchantDeadlineAt()).isNotNull();
  }

  /** Critério 3: durante o prazo, sem link de finalização contestável. */
  @Test
  void duringTheDeadlineThereIsNoContestableCompletionYet() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicarSemTelefone(merchant));
    acionar(courier, pedidoId);

    assertThat(contestavelLiberadoNoBanco(pedidoId)).isFalse();
  }

  /** Critério 4/5: code-dispatches destrava e, com telefone, corrige o pedido e envia SMS. */
  @Test
  void merchantDispatchUnlocksAndFixesThePhoneWhenProvided() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicarSemTelefone(merchant));
    acionar(courier, pedidoId);

    ResponseEntity<CodeDispatchResponse> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/delivery/code-dispatches"),
            HttpMethod.POST,
            authed(login(merchant).accessToken(), new CodeDispatchRequest("31998877665")),
            CodeDispatchResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(telefoneNoBanco(pedidoId)).isEqualTo("31998877665");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from contingencia_otp where pedido_id = ? and resultado = 'repassado'",
                Integer.class,
                pedidoId))
        .isEqualTo(1);
  }

  /** Critério 6: prazo vencido sem telefone e sem repasse → penalidade e contestável liberado. */
  @Test
  void deadlineExpiredWithoutPhoneAndWithoutDispatchPenalizesAndReleasesContestable() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicarSemTelefone(merchant));
    acionar(courier, pedidoId);
    vencerPrazo(pedidoId);

    contingencyExpiryJob.resolverPrazosVencidos();

    assertThat(contestavelLiberadoNoBanco(pedidoId)).isTrue();
    Integer penalidades =
        jdbcTemplate.queryForObject(
            "select count(*) from penalidade_estabelecimento where pedido_id = ?",
            Integer.class,
            pedidoId);
    assertThat(penalidades).isEqualTo(1);
    String motivo =
        jdbcTemplate.queryForObject(
            "select motivo from penalidade_estabelecimento where pedido_id = ?",
            String.class,
            pedidoId);
    assertThat(motivo).isNotBlank();
  }

  /** Critério 7: prazo vencido com repasse registrado → libera contestável sem penalidade. */
  @Test
  void deadlineExpiredWithDispatchReleasesContestableWithoutPenalty() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicarSemTelefone(merchant));
    acionar(courier, pedidoId);
    restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/code-dispatches"),
        HttpMethod.POST,
        authed(login(merchant).accessToken(), new CodeDispatchRequest(null)),
        CodeDispatchResponse.class);
    vencerPrazo(pedidoId);

    contingencyExpiryJob.resolverPrazosVencidos();

    assertThat(contestavelLiberadoNoBanco(pedidoId)).isTrue();
    Integer penalidades =
        jdbcTemplate.queryForObject(
            "select count(*) from penalidade_estabelecimento where pedido_id = ?",
            Integer.class,
            pedidoId);
    assertThat(penalidades).isZero();
  }

  /** Critério 8: contingência não mexe no contador de entregas do entregador. */
  @Test
  void contingencyNeverTouchesTheCouriersDeliveryCounter() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicarSemTelefone(merchant));
    Integer antes =
        jdbcTemplate.queryForObject(
            "select entregas_realizadas from entregador where usuario_id = ?",
            Integer.class,
            courier.id());

    acionar(courier, pedidoId);
    vencerPrazo(pedidoId);
    contingencyExpiryJob.resolverPrazosVencidos();

    Integer depois =
        jdbcTemplate.queryForObject(
            "select entregas_realizadas from entregador where usuario_id = ?",
            Integer.class,
            courier.id());
    assertThat(depois).isEqualTo(antes);
  }

  /** Critério 9: acionar de novo no mesmo degrau não reinicia o prazo. */
  @Test
  void triggeringAgainOnTheSameStepDoesNotRestartTheDeadline() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicarSemTelefone(merchant));
    Instant prazoOriginal = acionar(courier, pedidoId).getBody().merchantDeadlineAt();

    Instant prazoReentrante = acionar(courier, pedidoId).getBody().merchantDeadlineAt();

    assertThat(prazoReentrante).isEqualTo(prazoOriginal);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from contingencia_otp where pedido_id = ? and degrau = 2",
                Integer.class,
                pedidoId))
        .isEqualTo(1);
  }

  /** Estabelecimento é notificado com urgência no degrau 2. */
  @Test
  void theMerchantIsNotifiedUrgentlyOnStepTwo() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = aceitar(courier, publicarSemTelefone(merchant));

    acionar(courier, pedidoId);

    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              NotificationListResponse inbox =
                  restTemplate
                      .exchange(
                          baseUrl("/me/notifications?type=delivery.code_contingency&perPage=10"),
                          HttpMethod.GET,
                          new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                          NotificationListResponse.class)
                      .getBody();
              assertThat(inbox.data())
                  .anyMatch(n -> pedidoId.toString().equals(n.payload().get("orderId")));
              assertThat(inbox.data().get(0).priority().toString()).isEqualToIgnoringCase("urgent");
            });
  }

  private void vencerPrazo(UUID pedidoId) {
    jdbcTemplate.update(
        "update contingencia_otp set prazo_em = ? where pedido_id = ? and degrau = 2 and resultado = 'notificado'",
        java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        pedidoId);
  }

  private boolean contestavelLiberadoNoBanco(UUID pedidoId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "select contestavel_liberado from pedido where id = ?", Boolean.class, pedidoId));
  }

  private String telefoneNoBanco(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select recebedor_telefone from pedido where id = ?", String.class, pedidoId);
  }

  private ResponseEntity<CodeRecoveryResponse> acionar(RegisteredTestUser courier, UUID pedidoId) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/code-recoveries"),
        HttpMethod.POST,
        authed(login(courier).accessToken(), new CodeRecoveryRequest("receiver_without_code")),
        CodeRecoveryResponse.class);
  }

  private String lerCodigo(RegisteredTestUser merchant, UUID pedidoId) {
    return restTemplate
        .exchange(
            baseUrl("/orders/" + pedidoId + "/delivery/code"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(merchant).accessToken())),
            com.uaiou.orders.dto.DeliveryCodeResponse.class)
        .getBody()
        .code();
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
        authed(token, new UpdateLocationRequest(PERTO_LAT, PERTO_LNG, new BigDecimal("10.0"))),
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
