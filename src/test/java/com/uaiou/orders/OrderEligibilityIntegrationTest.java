package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderListResponse;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.orders.service.OrderPublishedFanout;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-11.5 a RF-11.10 — critérios de aceite 5, 6, 7, 8, 9 e 10 de T-11. */
class OrderEligibilityIntegrationTest extends AbstractAuthIntegrationTest {

  // Destino dos pedidos: Jardim Independência, BH.
  private static final String DEST_LAT = "-19.925100";
  private static final String DEST_LONG = "-43.941700";
  // ~1 km do destino — dentro do raio padrão (10 km).
  private static final String PERTO_LAT = "-19.918200";
  private static final String PERTO_LONG = "-43.938600";
  // Contagem/BH, ~30 km — fora do raio.
  private static final String LONGE_LAT = "-19.931700";
  private static final String LONGE_LONG = "-44.053800";

  @Autowired private OrderTestFixtures orders;
  @Autowired private OrderPublishedFanout fanout;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void anAvailableCourierWithinTheRadiusSeesTheOrder() {
    RegisteredTestUser courier = disponivelEm(PERTO_LAT, PERTO_LONG);
    UUID pedidoId = publicarPedido();

    OrderListResponse vitrine = vitrineDe(courier);

    assertThat(vitrine.warning()).isNull();
    assertThat(vitrine.data()).anyMatch(o -> o.id().equals(pedidoId));
    assertThat(vitrine.data()).allSatisfy(o -> assertThat(o.distanceKm()).isNotNull());
  }

  @Test
  void aCourierOutsideTheRadiusDoesNotSeeTheOrder() {
    RegisteredTestUser courier = disponivelEm(LONGE_LAT, LONGE_LONG);
    UUID pedidoId = publicarPedido();

    assertThat(vitrineDe(courier).data()).noneMatch(o -> o.id().equals(pedidoId));
  }

  @Test
  void anUnavailableCourierSeesNothing() {
    RegisteredTestUser courier = disponivelEm(PERTO_LAT, PERTO_LONG);
    publicarPedido();
    setAvailability(login(courier).accessToken(), false);

    OrderListResponse vitrine = vitrineDe(courier);

    assertThat(vitrine.data()).isEmpty();
    assertThat(vitrine.warning()).isEqualTo("LOCATION_STALE");
  }

  @Test
  void aCourierWithAStalePositionGetsAnEmptyListWithAWarning() {
    RegisteredTestUser courier = disponivelEm(PERTO_LAT, PERTO_LONG);
    publicarPedido();
    jdbcTemplate.update(
        "update entregador set localizacao_em = ? where usuario_id = ?",
        Timestamp.from(Instant.now().minus(30, ChronoUnit.MINUTES)),
        courier.id());

    OrderListResponse vitrine = vitrineDe(courier);

    // RF-11.8: lista vazia com aviso, nunca resultado calculado sobre posição obsoleta.
    assertThat(vitrine.data()).isEmpty();
    assertThat(vitrine.warning()).isEqualTo("LOCATION_STALE");
  }

  @Test
  void aPendingCourierSeesNothing() {
    RegisteredTestUser courier = registerCourier();
    // Ativa só para conseguir reportar posição, depois volta a pendente.
    moderation.activate(courier.id());
    String token = login(courier).accessToken();
    reportarPosicao(token, PERTO_LAT, PERTO_LONG);
    setAvailability(token, true);
    jdbcTemplate.update("update usuario set status = 'pendente' where id = ?", courier.id());
    publicarPedido();

    assertThat(vitrineDe(courier).data()).isEmpty();
  }

  /**
   * Critério de aceite 7: bloqueio do estabelecimento remove o pedido da vitrine (RF-11.5/T-12).
   */
  @Test
  void aBlockedCourierDoesNotSeeTheMerchantsOrder() {
    RegisteredTestUser courier = disponivelEm(PERTO_LAT, PERTO_LONG);
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    orders.bloquear(merchant.id(), courier.id(), "Teste de bloqueio");
    UUID pedidoId = publicarPedidoDe(merchant);

    assertThat(vitrineDe(courier).data()).noneMatch(o -> o.id().equals(pedidoId));
  }

  /** Critério de aceite 8: pedido de terceiro responde 404, não 403 — não revela existência. */
  @Test
  void anotherMerchantsOrderIsNotFound() {
    RegisteredTestUser dono = registerAndActivateMerchant();
    orders.darCreditos(dono.id(), 5);
    UUID pedidoId = publicarPedidoDe(dono);

    RegisteredTestUser estranho = registerAndActivateMerchant();
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(estranho).accessToken())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error().code()).isEqualTo("ORDER_NOT_FOUND");
  }

  @Test
  void anOutOfRangeCourierCannotReadTheOrderDetailEither() {
    UUID pedidoId = publicarPedido();
    RegisteredTestUser longe = disponivelEm(LONGE_LAT, LONGE_LONG);

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(longe).accessToken())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** Critério de aceite 9: distanceKm muda quando o entregador se move, sem tocar no pedido. */
  @Test
  void distanceKmChangesWhenTheCourierMovesAndTheOrderDoesNot() {
    RegisteredTestUser courier = disponivelEm(PERTO_LAT, PERTO_LONG);
    UUID pedidoId = publicarPedido();
    String token = login(courier).accessToken();

    BigDecimal antes = distanciaNaVitrine(courier, pedidoId);
    reportarPosicao(token, "-19.960000", "-43.990000");
    BigDecimal depois = distanciaNaVitrine(courier, pedidoId);

    assertThat(antes).isNotNull();
    assertThat(depois).isNotNull();
    assertThat(depois).isNotEqualByComparingTo(antes);
  }

  /** Critério de aceite 10: o fan-out pós-commit alcança exatamente os elegíveis. */
  @Test
  void publishFanoutTargetsOnlyEligibleCouriers() {
    RegisteredTestUser perto = disponivelEm(PERTO_LAT, PERTO_LONG);
    RegisteredTestUser longe = disponivelEm(LONGE_LAT, LONGE_LONG);
    RegisteredTestUser bloqueado = disponivelEm(PERTO_LAT, PERTO_LONG);

    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    orders.bloquear(merchant.id(), bloqueado.id(), "Teste de fan-out");
    publicarPedidoDe(merchant);

    // O fan-out roda depois do commit, em outra transação — esperar em vez de assumir.
    Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(fanout.ultimosDestinatarios()).contains(perto.id());
              assertThat(fanout.ultimosDestinatarios()).doesNotContain(longe.id(), bloqueado.id());
            });
  }

  private RegisteredTestUser disponivelEm(String lat, String lng) {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    reportarPosicao(token, lat, lng);
    setAvailability(token, true);
    return courier;
  }

  private void reportarPosicao(String token, String lat, String lng) {
    restTemplate.exchange(
        baseUrl("/me/location"),
        HttpMethod.PUT,
        authed(
            token,
            new UpdateLocationRequest(
                new BigDecimal(lat), new BigDecimal(lng), new BigDecimal("10.0"))),
        Void.class);
  }

  private void setAvailability(String token, boolean available) {
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(available)),
        Object.class);
  }

  private UUID publicarPedido() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    return publicarPedidoDe(merchant);
  }

  private UUID publicarPedidoDe(RegisteredTestUser merchant) {
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

  private OrderListResponse vitrineDe(RegisteredTestUser courier) {
    return restTemplate
        .exchange(
            baseUrl("/orders?status=published&perPage=100"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            OrderListResponse.class)
        .getBody();
  }

  private BigDecimal distanciaNaVitrine(RegisteredTestUser courier, UUID pedidoId) {
    return vitrineDe(courier).data().stream()
        .filter(o -> o.id().equals(pedidoId))
        .findFirst()
        .map(o -> o.distanceKm())
        .orElse(null);
  }
}
