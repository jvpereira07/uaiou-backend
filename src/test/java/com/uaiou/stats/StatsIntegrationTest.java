package com.uaiou.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DeliveryCodeResponse;
import com.uaiou.orders.dto.DeliveryCompletionRequest;
import com.uaiou.orders.dto.DeliveryCompletionResponse;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.money.Money;
import com.uaiou.stats.dto.CourierStatsResponse;
import com.uaiou.stats.dto.MerchantStatsResponse;
import com.uaiou.stats.dto.SeriesResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-22.1 a RF-22.8 — critérios de aceite de T-22. */
class StatsIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Critério 1: cada papel recebe o conjunto de métricas correspondente. */
  @Test
  void eachRoleReceivesItsOwnMetricSet() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    finalizarPedido(merchant, courier);

    CourierStatsResponse doEntregador =
        restTemplate
            .exchange(
                baseUrl("/me/stats?period=30d"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(courier).accessToken())),
                CourierStatsResponse.class)
            .getBody();
    MerchantStatsResponse doEstabelecimento =
        restTemplate
            .exchange(
                baseUrl("/me/stats?period=30d"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                MerchantStatsResponse.class)
            .getBody();

    assertThat(doEntregador.period().label()).isEqualTo("30d");
    assertThat(doEstabelecimento.period().label()).isEqualTo("30d");
  }

  /** Critério 2: números batem com o banco para um cenário conhecido. */
  @Test
  void numbersMatchDirectDatabaseQueriesForAKnownScenario() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    finalizarPedido(merchant, courier);
    finalizarPedido(merchant, courier);

    CourierStatsResponse stats =
        restTemplate
            .exchange(
                baseUrl("/me/stats?period=30d"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(courier).accessToken())),
                CourierStatsResponse.class)
            .getBody();

    Integer finalizadosNoBanco =
        jdbcTemplate.queryForObject(
            "select count(*) from pedido where entregador_id = ? and status = 'finalizado'",
            Integer.class,
            courier.id());
    assertThat(stats.deliveriesCompleted()).isEqualTo(finalizadosNoBanco);
  }

  /** Critério 3: codeContingencyRate sempre aparece para o estabelecimento. */
  @Test
  void codeContingencyRateAlwaysAppearsForTheMerchant() {
    RegisteredTestUser merchant = merchantComCredito();

    MerchantStatsResponse stats =
        restTemplate
            .exchange(
                baseUrl("/me/stats?period=30d"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                MerchantStatsResponse.class)
            .getBody();

    assertThat(stats.codeContingencyRate()).isNotNull();
  }

  /** Critério 4: entregador vê "a receber" e "acertado" coerentes com o livro-razão. */
  @Test
  void courierSeesReceivableAndSettledConsistentWithTheLedger() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    UUID lancamentoId =
        jdbcTemplate.queryForObject(
            "select id from lancamento_frete where pedido_id = ?", UUID.class, pedidoId);
    restTemplate.exchange(
        baseUrl("/me/earnings/settlements"),
        HttpMethod.POST,
        authed(
            login(courier).accessToken(),
            new com.uaiou.delivery.dto.SettlementRequest(java.util.List.of(lancamentoId))),
        Object.class);

    CourierStatsResponse stats =
        restTemplate
            .exchange(
                baseUrl("/me/stats?period=30d"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(courier).accessToken())),
                CourierStatsResponse.class)
            .getBody();

    assertThat(stats.earningsReceivable()).isEqualTo(Money.ZERO);
    assertThat(stats.earningsSettled()).isEqualTo(Money.of("6.00"));
  }

  /** Critério 6: série temporal com dia sem atividade retorna 0, nunca omite o ponto. */
  @Test
  void seriesReturnsZeroForDaysWithoutActivityInsteadOfOmittingThem() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    finalizarPedido(merchant, courier);

    SeriesResponse serie =
        restTemplate
            .exchange(
                baseUrl("/me/stats/series?metric=deliveries"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(courier).accessToken())),
                SeriesResponse.class)
            .getBody();

    assertThat(serie.data()).hasSize(31);
    assertThat(serie.data()).anyMatch(p -> p.value().intValue() == 0);
    long somaTotal = serie.data().stream().mapToLong(p -> p.value().longValue()).sum();
    assertThat(somaTotal).isEqualTo(1);
  }

  private UUID finalizarPedido(RegisteredTestUser merchant, RegisteredTestUser courier) {
    UUID pedidoId = publicar(merchant);
    restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/assignment"),
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(login(courier).accessToken())),
        AssignmentResponse.class);
    marcarColetado(pedidoId);
    String codigo =
        restTemplate
            .exchange(
                baseUrl("/orders/" + pedidoId + "/delivery/code"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                DeliveryCodeResponse.class)
            .getBody()
            .code();
    ResponseEntity<DeliveryCompletionResponse> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/delivery/completion"),
            HttpMethod.POST,
            authed(
                login(courier).accessToken(),
                new DeliveryCompletionRequest(
                    DeliveryCompletionRequest.Mode.CODE, codigo, null, DEST_LAT, DEST_LNG)),
            DeliveryCompletionResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.CREATED);
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
