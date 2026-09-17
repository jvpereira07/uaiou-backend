package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderLifecycleResponse;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Critérios de aceite de T-26 — chegada, coleta, cancelamento e desistência. */
class PickupCancellationIntegrationTest extends AbstractAuthIntegrationTest {

  private static final String DEST_LAT = "-19.925100";
  private static final String DEST_LONG = "-43.941700";
  private static final String LOJA_LAT = "-19.930000";
  private static final String LOJA_LONG = "-43.935000";
  // ~40 m da loja: dentro do raio de coleta.
  private static final String NA_LOJA_LAT = "-19.930300";
  private static final String NA_LOJA_LONG = "-43.935200";
  // ~1,4 km da loja: fora do raio de coleta, dentro do raio de elegibilidade.
  private static final String LONGE_LAT = "-19.918200";
  private static final String LONGE_LONG = "-43.938600";

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Critérios 1, 2 e 4. */
  @Test
  void twoReadingsInsideTheRadiusRegisterArrivalAndTheMerchantConfirmsPickup() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "9.00");
    aceitar(courier, pedidoId).getStatusCode();

    reportarPosicao(courier, NA_LOJA_LAT, NA_LOJA_LONG);
    reportarPosicao(courier, LONGE_LAT, LONGE_LONG);
    reportarPosicao(courier, NA_LOJA_LAT, NA_LOJA_LONG);
    assertThat(pedidoDe(merchant, pedidoId).arrivedAt())
        .as("uma leitura dentro seguida de uma fora não registra chegada")
        .isNull();

    reportarPosicao(courier, NA_LOJA_LAT, NA_LOJA_LONG);
    OrderResponse aposChegada = pedidoDe(merchant, pedidoId);
    assertThat(aposChegada.arrivedAt()).isNotNull();
    assertThat(aposChegada.links()).containsKeys("pickupConfirmation", "cancellation");
    assertThat(notificacoesDoTipo(merchant.id(), "order.courier_arrived")).isEqualTo(1);

    ResponseEntity<OrderLifecycleResponse> coleta =
        post(
            merchant,
            "/orders/" + pedidoId + "/pickup/confirmation",
            null,
            OrderLifecycleResponse.class);
    assertThat(coleta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(coleta.getBody().status()).isEqualTo(OrderStatus.PICKED_UP);
    assertThat(orders.statusPersistidoDe(pedidoId)).isEqualTo("coletado");
  }

  /** Critério 3. */
  @Test
  void manualArrivalFarFromTheMerchantIsRefusedWithTheDistance() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "9.00");
    aceitar(courier, pedidoId);

    ResponseEntity<ErrorResponse> resposta =
        post(
            courier,
            "/orders/" + pedidoId + "/pickup/arrival",
            Map.of("lat", LONGE_LAT, "lng", LONGE_LONG),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("OUTSIDE_PICKUP_RADIUS");
  }

  /** Critério 5. */
  @Test
  void confirmingPickupBeforeTheCourierArrivesIsRefused() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "9.00");
    aceitar(courier, pedidoId);

    ResponseEntity<ErrorResponse> resposta =
        post(merchant, "/orders/" + pedidoId + "/pickup/confirmation", null, ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("COURIER_NOT_AT_PICKUP");
  }

  /** Critério 7. */
  @Test
  void cancellingAPublishedOrderCostsNothing() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicar(merchant, "9.00");

    ResponseEntity<OrderLifecycleResponse> resposta =
        cancelar(merchant, pedidoId, "customer_gave_up", null);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody().status()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(resposta.getBody().cancellationFee()).isNull();
    assertThat(lancamentosDe(pedidoId)).isZero();
  }

  /** Critério 8. */
  @Test
  void cancellingBeforeTheCourierArrivesCostsNothingButNotifiesTheCourier() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "9.00");
    aceitar(courier, pedidoId);

    ResponseEntity<OrderLifecycleResponse> resposta =
        cancelar(merchant, pedidoId, "out_of_stock", null);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(lancamentosDe(pedidoId)).isZero();
    assertThat(notificacoesDoTipo(courier.id(), "order.cancelled")).isEqualTo(1);
  }

  /** Critério 9: R$ 9,00 cancelado após a chegada → R$ 4,50 a receber, com o percentual gravado. */
  @Test
  void cancellingAfterArrivalChargesHalfTheFinalFee() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "9.00");
    aceitar(courier, pedidoId);
    chegarManualmente(courier, pedidoId);

    assertThat(pedidoDe(merchant, pedidoId).pendingCancellationFee()).isEqualTo(Money.of("4.50"));

    ResponseEntity<OrderLifecycleResponse> resposta =
        cancelar(merchant, pedidoId, "customer_gave_up", null);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody().cancellationFee()).isEqualTo(Money.of("4.50"));
    Map<String, Object> lancamento =
        jdbcTemplate.queryForMap(
            "select tipo, valor::text as valor, taxa_percentual::text as taxa, status"
                + " from lancamento_frete where pedido_id = ?",
            pedidoId);
    assertThat(lancamento)
        .containsEntry("tipo", "taxa_cancelamento")
        .containsEntry("valor", "4.50")
        .containsEntry("taxa", "0.5000")
        .containsEntry("status", "a_receber");
  }

  /** Critério 10: arredondamento HALF_UP. */
  @Test
  void theCancellationFeeRoundsHalfUp() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "7.25");
    aceitar(courier, pedidoId);
    chegarManualmente(courier, pedidoId);

    ResponseEntity<OrderLifecycleResponse> resposta =
        cancelar(merchant, pedidoId, "order_error", null);

    assertThat(resposta.getBody().cancellationFee()).isEqualTo(Money.of("3.63"));
  }

  /** Critério 11. */
  @Test
  void aPickedUpOrderCanNoLongerBeCancelled() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "9.00");
    aceitar(courier, pedidoId);
    marcarColetado(pedidoId);

    ResponseEntity<ErrorResponse> resposta =
        post(
            merchant,
            "/orders/" + pedidoId + "/cancellation",
            Map.of("reason", "customer_gave_up"),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resposta.getBody().error().code()).isEqualTo("ORDER_NOT_CANCELLABLE");
  }

  /** Critério 12. */
  @Test
  void otherWithoutANoteIsRefused() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicar(merchant, "9.00");

    ResponseEntity<ErrorResponse> resposta =
        post(
            merchant,
            "/orders/" + pedidoId + "/cancellation",
            Map.of("reason", "other"),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("NOTE_REQUIRED");
  }

  /** Critérios 17, 19 e 20: desistir devolve o pedido à vitrine sem custo ao estabelecimento. */
  @Test
  void withdrawingRepublishesTheOrderAtNoCostToTheMerchant() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "9.00");
    aceitar(courier, pedidoId);
    int creditosAntes = orders.saldoDe(merchant.id());

    ResponseEntity<OrderLifecycleResponse> resposta =
        desistir(courier, pedidoId, "vehicle_problem");

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody().status()).isEqualTo(OrderStatus.PUBLISHED);
    OrderResponse pedido = pedidoDe(merchant, pedidoId);
    assertThat(pedido.courier()).isNull();
    assertThat(pedido.finalFee()).isNull();
    assertThat(orders.saldoDe(merchant.id())).isEqualTo(creditosAntes);
    assertThat(lancamentosDe(pedidoId)).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from otp where pedido_id = ?", Integer.class, pedidoId))
        .isZero();
    assertThat(notificacoesDoTipo(merchant.id(), "order.courier_withdrew")).isEqualTo(1);

    ResponseEntity<ErrorResponse> reaceite =
        post(courier, "/orders/" + pedidoId + "/assignment", null, ErrorResponse.class);
    assertThat(reaceite.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(reaceite.getBody().error().code()).isEqualTo("ORDER_WITHDRAWN_BY_COURIER");

    RegisteredTestUser outro = disponivelEm(LONGE_LAT, LONGE_LONG);
    assertThat(aceitar(outro, pedidoId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from otp where pedido_id = ?", Integer.class, pedidoId))
        .isEqualTo(1);
  }

  /** Critério 18: contraoferta aceita morre com a desistência. */
  @Test
  void withdrawingFromACounterofferReturnsToTheProposedFee() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "7.00");
    aceitar(courier, pedidoId);
    jdbcTemplate.update("update pedido set frete_final = 9.00 where id = ?", pedidoId);

    desistir(courier, pedidoId, "personal");

    OrderResponse pedido = pedidoDe(merchant, pedidoId);
    assertThat(pedido.proposedFee()).isEqualTo(Money.of("7.00"));
    assertThat(pedido.finalFee()).isNull();
  }

  /** Critério 21. */
  @Test
  void aPickedUpOrderCannotBeWithdrawnFrom() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicar(merchant, "9.00");
    aceitar(courier, pedidoId);
    marcarColetado(pedidoId);

    ResponseEntity<ErrorResponse> resposta =
        post(
            courier,
            "/orders/" + pedidoId + "/withdrawal",
            Map.of("reason", "personal"),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resposta.getBody().error().code()).isEqualTo("ORDER_NOT_WITHDRAWABLE");
  }

  /** Critério 22. */
  @Test
  void theThirdCountedWithdrawalBlocksNewAcceptances() {
    RegisteredTestUser merchant = merchantComCoordenada();
    orders.darCreditos(merchant.id(), 10);
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);

    for (int i = 0; i < 3; i++) {
      UUID pedidoId = publicar(merchant, "9.00");
      aceitar(courier, pedidoId);
      desistir(courier, pedidoId, "personal");
    }

    UUID quarto = publicar(merchant, "9.00");
    ResponseEntity<ErrorResponse> resposta =
        post(courier, "/orders/" + quarto + "/assignment", null, ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("WITHDRAWAL_LIMIT_REACHED");
  }

  /** Critério 23: atraso da loja além do tolerado não penaliza; dentro do tolerado, penaliza. */
  @Test
  void aLongWaitAtTheMerchantMakesAPickupDelayWithdrawalFree() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);

    UUID longaEspera = publicar(merchant, "9.00");
    aceitar(courier, longaEspera);
    jdbcTemplate.update(
        "update pedido set chegou_em = now() - interval '20 minutes' where id = ?", longaEspera);
    desistir(courier, longaEspera, "pickup_delay");

    UUID curtaEspera = publicar(merchant, "9.00");
    aceitar(courier, curtaEspera);
    jdbcTemplate.update(
        "update pedido set chegou_em = now() - interval '5 minutes' where id = ?", curtaEspera);
    desistir(courier, curtaEspera, "pickup_delay");

    assertThat(contaPenalidade(longaEspera)).isFalse();
    assertThat(contaPenalidade(curtaEspera)).isTrue();
  }

  private Boolean contaPenalidade(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select conta_penalidade from desistencia_pedido where pedido_id = ?",
        Boolean.class,
        pedidoId);
  }

  private void chegarManualmente(RegisteredTestUser courier, UUID pedidoId) {
    ResponseEntity<OrderLifecycleResponse> resposta =
        post(
            courier,
            "/orders/" + pedidoId + "/pickup/arrival",
            Map.of("lat", NA_LOJA_LAT, "lng", NA_LOJA_LONG),
            OrderLifecycleResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody().arrivedAt()).isNotNull();
  }

  private ResponseEntity<OrderLifecycleResponse> cancelar(
      RegisteredTestUser merchant, UUID pedidoId, String motivo, String nota) {
    Map<String, Object> corpo =
        nota == null ? Map.of("reason", motivo) : Map.of("reason", motivo, "note", nota);
    return post(
        merchant, "/orders/" + pedidoId + "/cancellation", corpo, OrderLifecycleResponse.class);
  }

  private ResponseEntity<OrderLifecycleResponse> desistir(
      RegisteredTestUser courier, UUID pedidoId, String motivo) {
    ResponseEntity<OrderLifecycleResponse> resposta =
        post(
            courier,
            "/orders/" + pedidoId + "/withdrawal",
            Map.of("reason", motivo),
            OrderLifecycleResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    return resposta;
  }

  private ResponseEntity<Object> aceitar(RegisteredTestUser courier, UUID pedidoId) {
    ResponseEntity<Object> resposta =
        post(courier, "/orders/" + pedidoId + "/assignment", null, Object.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return resposta;
  }

  private <T> ResponseEntity<T> post(
      RegisteredTestUser user, String path, Object body, Class<T> tipo) {
    return restTemplate.exchange(
        baseUrl(path),
        HttpMethod.POST,
        new HttpEntity<>(body, authHeaders(login(user).accessToken())),
        tipo);
  }

  private OrderResponse pedidoDe(RegisteredTestUser user, UUID pedidoId) {
    return restTemplate
        .exchange(
            baseUrl("/orders/" + pedidoId),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(user).accessToken())),
            OrderResponse.class)
        .getBody();
  }

  private int lancamentosDe(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from lancamento_frete where pedido_id = ?", Integer.class, pedidoId);
  }

  private int notificacoesDoTipo(UUID usuarioId, String tipo) {
    return jdbcTemplate.queryForObject(
        "select count(*) from notificacao where usuario_id = ? and tipo = ?",
        Integer.class,
        usuarioId,
        tipo);
  }

  private void reportarPosicao(RegisteredTestUser courier, String lat, String lng) {
    restTemplate.exchange(
        baseUrl("/me/location"),
        HttpMethod.PUT,
        authed(
            login(courier).accessToken(),
            new UpdateLocationRequest(
                new BigDecimal(lat), new BigDecimal(lng), new BigDecimal("10.0"))),
        Void.class);
  }

  private RegisteredTestUser merchantComCoordenada() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    jdbcTemplate.update(
        "update estabelecimento set lat = ?, \"long\" = ? where usuario_id = ?",
        new BigDecimal(LOJA_LAT),
        new BigDecimal(LOJA_LONG),
        merchant.id());
    return merchant;
  }

  private RegisteredTestUser disponivelEm(String lat, String lng) {
    RegisteredTestUser courier = registerAndActivateCourier();
    reportarPosicao(courier, lat, lng);
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(login(courier).accessToken(), new UpdateAvailabilityRequest(true)),
        Object.class);
    return courier;
  }

  private UUID publicar(RegisteredTestUser merchant, String frete) {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Money.of(frete),
            null,
            new DestinationRequest(
                "Rua Beija-Flor",
                "45",
                null,
                "Jardim Independencia",
                "Belo Horizonte",
                new BigDecimal(DEST_LAT),
                new BigDecimal(DEST_LONG)),
            new CreateOrderRequest.ReceiverRequest("Marina Alves", "31998877665"));
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
