package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.notifications.dto.NotificationListResponse;
import com.uaiou.orders.dto.CounterofferDecisionRequest;
import com.uaiou.orders.dto.CounterofferDecisionResponse;
import com.uaiou.orders.dto.CounterofferResponse;
import com.uaiou.orders.dto.CreateCounterofferRequest;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderListResponse;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-14.1 a RF-14.9 — critérios de aceite de T-14. */
class CounterofferIntegrationTest extends AbstractAuthIntegrationTest {

  private static final String PERTO_LAT = "-19.918200";
  private static final String PERTO_LONG = "-43.938600";

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Critério 1: contraoferta em pedido elegível retorna 201 e move para em_negociacao. */
  @Test
  void proposingOnAnEligibleOrderReturns201AndMovesToNegotiation() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchantComCredito());

    ResponseEntity<CounterofferResponse> response = propor(courier, pedidoId, "9.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().proposedFee()).isEqualTo(Money.of("9.00"));
    assertThat(response.getBody().status().toString()).isEqualTo("PENDING");
    assertThat(statusNoBanco(pedidoId)).isEqualTo("em_negociacao");
  }

  /** Critério 2: segunda contraoferta pendente do mesmo entregador → 409. */
  @Test
  void aSecondPendingCounterofferFromTheSameCourierIsAConflict() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchantComCredito());
    propor(courier, pedidoId, "9.00");

    ResponseEntity<ErrorResponse> response = proporComErro(courier, pedidoId, "10.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("COUNTEROFFER_ALREADY_PENDING");
  }

  /**
   * Critério 3: outro entregador ainda aceita direto; a pendente vira invalidada e o autor é
   * notificado.
   */
  @Test
  void whileACounterofferIsPendingAnotherCourierCanStillAcceptDirectly() {
    RegisteredTestUser proponente = disponivel();
    RegisteredTestUser aceitanteDireto = disponivel();
    UUID pedidoId = publicar(merchantComCredito());
    UUID contraofertaId = propor(proponente, pedidoId, "9.00").getBody().id();

    ResponseEntity<Object> aceite =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/assignment"),
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(login(aceitanteDireto).accessToken())),
            Object.class);
    assertThat(aceite.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    assertThat(contraofertaStatusNoBanco(contraofertaId)).isEqualTo("invalidada");
    assertThat(entregadorNoBanco(pedidoId)).isEqualTo(aceitanteDireto.id());
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(temNotificacao(proponente, "counteroffer.decided", pedidoId)).isTrue());
  }

  /** Critério 4: aceitar atribui pelo VALOR PROPOSTO e invalida as demais. */
  @Test
  void acceptingAssignsAtTheProposedValueAndInvalidatesTheRest() {
    RegisteredTestUser vencedor = disponivel();
    RegisteredTestUser outro = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    UUID vencedoraId = propor(vencedor, pedidoId, "9.00").getBody().id();
    UUID outraId = propor(outro, pedidoId, "8.50").getBody().id();

    ResponseEntity<CounterofferDecisionResponse> decisao =
        decidir(merchant, vencedoraId, CounterofferDecisionRequest.Outcome.ACCEPTED);

    assertThat(decisao.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(decisao.getBody().order().finalFee()).isEqualTo(Money.of("9.00"));
    assertThat(decisao.getBody().order().status()).isEqualTo(OrderStatus.ACCEPTED);
    assertThat(entregadorNoBanco(pedidoId)).isEqualTo(vencedor.id());
    assertThat(contraofertaStatusNoBanco(outraId)).isEqualTo("invalidada");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from otp where pedido_id = ?", Integer.class, pedidoId))
        .isEqualTo(1);
  }

  /** Critério 5: recusar mantém o pedido disponível para os demais e notifica o proponente. */
  @Test
  void rejectingKeepsTheOrderAvailableAndNotifiesTheProponent() {
    RegisteredTestUser proponente = disponivel();
    RegisteredTestUser outro = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    UUID contraofertaId = propor(proponente, pedidoId, "9.00").getBody().id();

    ResponseEntity<CounterofferDecisionResponse> decisao =
        decidir(merchant, contraofertaId, CounterofferDecisionRequest.Outcome.REJECTED);

    assertThat(decisao.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(statusNoBanco(pedidoId)).isEqualTo("publicado");
    assertThat(vitrine(outro).data()).anyMatch(o -> o.id().equals(pedidoId));
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(temNotificacao(proponente, "counteroffer.decided", pedidoId)).isTrue());
  }

  /** Recusa com outra pendente NÃO devolve o pedido a publicado (RF-14.7). */
  @Test
  void rejectingWithAnotherPendingDoesNotReopenTheOrder() {
    RegisteredTestUser a = disponivel();
    RegisteredTestUser b = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    UUID contraofertaA = propor(a, pedidoId, "9.00").getBody().id();
    propor(b, pedidoId, "8.00");

    decidir(merchant, contraofertaA, CounterofferDecisionRequest.Outcome.REJECTED);

    assertThat(statusNoBanco(pedidoId)).isEqualTo("em_negociacao");
  }

  /** Critério 6: decidir contraoferta já invalidada → 409 sem alterar o pedido. */
  @Test
  void decidingAnAlreadyInvalidatedCounterofferIsAConflictWithNoSideEffect() {
    RegisteredTestUser proponente = disponivel();
    RegisteredTestUser aceitanteDireto = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    UUID contraofertaId = propor(proponente, pedidoId, "9.00").getBody().id();
    restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/assignment"),
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(login(aceitanteDireto).accessToken())),
        Object.class);

    ResponseEntity<ErrorResponse> response =
        decidirComErro(merchant, contraofertaId, CounterofferDecisionRequest.Outcome.ACCEPTED);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("COUNTEROFFER_NO_LONGER_VALID");
    assertThat(entregadorNoBanco(pedidoId)).isEqualTo(aceitanteDireto.id());
  }

  /** Critério 7: aceite direto e aceite de contraoferta simultâneos → um único entregador. */
  @Test
  void concurrentDirectAndCounterofferAcceptsProduceExactlyOneWinner() throws Exception {
    RegisteredTestUser proponente = disponivel();
    RegisteredTestUser direto = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    UUID contraofertaId = propor(proponente, pedidoId, "9.00").getBody().id();
    String tokenMerchant = login(merchant).accessToken();
    String tokenDireto = login(direto).accessToken();

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch gate = new CountDownLatch(1);
    Future<ResponseEntity<Object>> viaContraoferta =
        pool.submit(
            () -> {
              gate.await();
              return restTemplate.exchange(
                  baseUrl("/counteroffers/" + contraofertaId + "/decision"),
                  HttpMethod.PUT,
                  authed(
                      tokenMerchant,
                      new CounterofferDecisionRequest(
                          CounterofferDecisionRequest.Outcome.ACCEPTED)),
                  Object.class);
            });
    Future<ResponseEntity<Object>> viaDireto =
        pool.submit(
            () -> {
              gate.await();
              return restTemplate.exchange(
                  baseUrl("/orders/" + pedidoId + "/assignment"),
                  HttpMethod.POST,
                  new HttpEntity<>(authHeaders(tokenDireto)),
                  Object.class);
            });
    gate.countDown();

    HttpStatus s1 = (HttpStatus) viaContraoferta.get(30, TimeUnit.SECONDS).getStatusCode();
    HttpStatus s2 = (HttpStatus) viaDireto.get(30, TimeUnit.SECONDS).getStatusCode();
    pool.shutdown();

    // Cada rota tem seu próprio código de sucesso (200 na decisão, 201 no aceite direto) — o que
    // importa é que exatamente uma das duas venceu e a outra perdeu a corrida (409).
    boolean contraofertaVenceu = s1 == HttpStatus.OK;
    boolean diretoVenceu = s2 == HttpStatus.CREATED;
    assertThat(contraofertaVenceu ^ diretoVenceu).isTrue();
    assertThat(contraofertaVenceu ? s2 : s1).isEqualTo(HttpStatus.CONFLICT);
    UUID vencedor = entregadorNoBanco(pedidoId);
    assertThat(vencedor).isIn(proponente.id(), direto.id());
  }

  /** Critério 8: entregador bloqueado não consegue contrapropor → 403. */
  @Test
  void aBlockedCourierCannotCounteroffer() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    orders.bloquear(merchant.id(), courier.id(), "Bloqueado");

    ResponseEntity<ErrorResponse> response = proporComErro(courier, pedidoId, "9.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("BLOCKED_BY_MERCHANT");
  }

  /** Critério 9: a listagem para o estabelecimento traz o score do proponente. */
  @Test
  void theMerchantListingCarriesTheProponentsScore() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    propor(courier, pedidoId, "9.00");

    List<CounterofferResponse> lista =
        restTemplate
            .exchange(
                baseUrl("/orders/" + pedidoId + "/counteroffers"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                new ParameterizedTypeReference<List<CounterofferResponse>>() {})
            .getBody();

    assertThat(lista).hasSize(1);
    assertThat(lista.get(0).courierId()).isEqualTo(courier.id());
    assertThat(lista.get(0).courierName()).isNotBlank();
    // Entregador novo, sem histórico: score nulo (T-20 formaliza "sem base"), não presença do
    // campo.
    assertThat(lista.get(0).courierScore()).isNull();
  }

  @Test
  void aMerchantCannotProposeAndACourierCannotDecide() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);

    ResponseEntity<ErrorResponse> naoEntregador =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/counteroffers"),
            HttpMethod.POST,
            authed(login(merchant).accessToken(), new CreateCounterofferRequest(Money.of("9.00"))),
            ErrorResponse.class);
    assertThat(naoEntregador.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    UUID contraofertaId = propor(courier, pedidoId, "9.00").getBody().id();
    ResponseEntity<ErrorResponse> naoEstabelecimento =
        decidirComErro(courier, contraofertaId, CounterofferDecisionRequest.Outcome.ACCEPTED);
    assertThat(naoEstabelecimento.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private ResponseEntity<CounterofferResponse> propor(
      RegisteredTestUser courier, UUID pedidoId, String valor) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/counteroffers"),
        HttpMethod.POST,
        authed(login(courier).accessToken(), new CreateCounterofferRequest(Money.of(valor))),
        CounterofferResponse.class);
  }

  private ResponseEntity<ErrorResponse> proporComErro(
      RegisteredTestUser courier, UUID pedidoId, String valor) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/counteroffers"),
        HttpMethod.POST,
        authed(login(courier).accessToken(), new CreateCounterofferRequest(Money.of(valor))),
        ErrorResponse.class);
  }

  private ResponseEntity<CounterofferDecisionResponse> decidir(
      RegisteredTestUser merchant,
      UUID contraofertaId,
      CounterofferDecisionRequest.Outcome outcome) {
    return restTemplate.exchange(
        baseUrl("/counteroffers/" + contraofertaId + "/decision"),
        HttpMethod.PUT,
        authed(login(merchant).accessToken(), new CounterofferDecisionRequest(outcome)),
        CounterofferDecisionResponse.class);
  }

  private ResponseEntity<ErrorResponse> decidirComErro(
      RegisteredTestUser merchant,
      UUID contraofertaId,
      CounterofferDecisionRequest.Outcome outcome) {
    return restTemplate.exchange(
        baseUrl("/counteroffers/" + contraofertaId + "/decision"),
        HttpMethod.PUT,
        authed(login(merchant).accessToken(), new CounterofferDecisionRequest(outcome)),
        ErrorResponse.class);
  }

  private boolean temNotificacao(RegisteredTestUser user, String tipo, UUID pedidoId) {
    NotificationListResponse inbox =
        restTemplate
            .exchange(
                baseUrl("/me/notifications?type=" + tipo + "&perPage=100"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(user).accessToken())),
                NotificationListResponse.class)
            .getBody();
    return inbox.data().stream()
        .anyMatch(n -> pedidoId.toString().equals(n.payload().get("orderId")));
  }

  private String statusNoBanco(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select status from pedido where id = ?", String.class, pedidoId);
  }

  private String contraofertaStatusNoBanco(UUID contraofertaId) {
    return jdbcTemplate.queryForObject(
        "select status from contraoferta where id = ?", String.class, contraofertaId);
  }

  private UUID entregadorNoBanco(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select entregador_id from pedido where id = ?", UUID.class, pedidoId);
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
        authed(
            token,
            new UpdateLocationRequest(
                new BigDecimal(PERTO_LAT), new BigDecimal(PERTO_LONG), new BigDecimal("10.0"))),
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
                new BigDecimal("-19.925100"),
                new BigDecimal("-43.941700")),
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

  private OrderListResponse vitrine(RegisteredTestUser courier) {
    return restTemplate
        .exchange(
            baseUrl("/orders?status=published&perPage=100"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            OrderListResponse.class)
        .getBody();
  }
}
