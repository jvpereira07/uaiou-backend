package com.uaiou.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.dto.FinancialAdjustmentRequest;
import com.uaiou.admin.dto.FinancialAdjustmentResponse;
import com.uaiou.delivery.dto.EarningsResponse;
import com.uaiou.delivery.dto.PayablesResponse;
import com.uaiou.delivery.dto.SettlementRequest;
import com.uaiou.delivery.dto.SettlementResponse;
import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DeliveryCodeResponse;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-18.1 a RF-18.8 — critérios de aceite de T-18. */
class LedgerIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Critério 1: finalizar cria exatamente um lançamento a_receber com valor = frete_final. */
  @Test
  void finalizingCreatesExactlyOneReceivableEntryWithTheFinalFee() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);

    Integer total =
        jdbcTemplate.queryForObject(
            "select count(*) from lancamento_frete where pedido_id = ?", Integer.class, pedidoId);
    assertThat(total).isEqualTo(1);

    var linha =
        jdbcTemplate.queryForMap(
            "select valor, status from lancamento_frete where pedido_id = ?", pedidoId);
    assertThat(((BigDecimal) linha.get("valor")).setScale(2)).isEqualByComparingTo("6.00");
    assertThat(linha.get("status")).isEqualTo("a_receber");
  }

  /** Critério 3: GET /me/earnings mostra total, a receber e acertado coerentes. */
  @Test
  void earningsShowsTotalsConsistentWithTheStatement() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    finalizarPedido(merchant, courier);
    finalizarPedido(merchant, courier);

    EarningsResponse earnings = earnings(courier);

    assertThat(earnings.data()).hasSize(2);
    assertThat(earnings.summary().receivable()).isEqualTo(Money.of("12.00"));
    assertThat(earnings.summary().settled()).isEqualTo(Money.ZERO);
    assertThat(earnings.summary().total()).isEqualTo(Money.of("12.00"));
  }

  /** Critério 5: confirmar move para acertado e reduz "a receber"; repetir → 409. */
  @Test
  void settlingMovesToSettledAndReducesReceivableRepeatingIsAConflict() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    UUID lancamentoId = lancamentoDoPedido(pedidoId);

    ResponseEntity<SettlementResponse> resposta = settle(courier, lancamentoId);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);

    EarningsResponse depois = earnings(courier);
    assertThat(depois.summary().receivable()).isEqualTo(Money.ZERO);
    assertThat(depois.summary().settled()).isEqualTo(Money.of("6.00"));

    ResponseEntity<ErrorResponse> repetir = settleComErro(courier, lancamentoId);
    assertThat(repetir.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(repetir.getBody().error().code()).isEqualTo("ALREADY_SETTLED");
  }

  /** Critério 6: entregador não confirma lançamento alheio → 403. */
  @Test
  void aCourierCannotSettleSomeoneElsesEarning() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser outroCourier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    UUID lancamentoId = lancamentoDoPedido(pedidoId);

    ResponseEntity<ErrorResponse> resposta = settleComErro(outroCourier, lancamentoId);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resposta.getBody().error().code()).isEqualTo("NOT_YOUR_EARNING");
  }

  /** Critério 4: GET /me/payables do estabelecimento espelha os mesmos valores, por entregador. */
  @Test
  void payablesMirrorsTheSameValuesGroupedByCourier() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    finalizarPedido(merchant, courier);

    PayablesResponse payables =
        restTemplate
            .exchange(
                baseUrl("/me/payables"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                PayablesResponse.class)
            .getBody();

    assertThat(payables.total()).isEqualTo(Money.of("6.00"));
    assertThat(payables.byCourier()).hasSize(1);
    assertThat(payables.byCourier().get(0).courierId()).isEqualTo(courier.id());
    assertThat(payables.byCourier().get(0).total()).isEqualTo(Money.of("6.00"));
  }

  /** Critério 8: ajuste sem reference → 422; com reference, audita e preserva o original. */
  @Test
  void adjustmentWithoutReferenceFailsAndWithReferencePreservesTheOriginal() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    UUID lancamentoId = lancamentoDoPedido(pedidoId);
    String adminToken = loginAdmin().accessToken();

    ResponseEntity<ErrorResponse> semReferencia =
        restTemplate.exchange(
            baseUrl("/admin/financial-adjustments"),
            HttpMethod.POST,
            authed(
                adminToken,
                new FinancialAdjustmentRequest(
                    "payout_correction", courier.id(), 550, "Correção", null, lancamentoId)),
            ErrorResponse.class);
    assertThat(semReferencia.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

    ResponseEntity<FinancialAdjustmentResponse> comReferencia =
        restTemplate.exchange(
            baseUrl("/admin/financial-adjustments"),
            HttpMethod.POST,
            authed(
                adminToken,
                new FinancialAdjustmentRequest(
                    "payout_correction",
                    courier.id(),
                    550,
                    "Correção de valor",
                    new FinancialAdjustmentRequest.ReferenceRef(
                        "support_ticket", UUID.randomUUID()),
                    lancamentoId)),
            FinancialAdjustmentResponse.class);
    assertThat(comReferencia.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // O lançamento original não foi reescrito.
    var original =
        jdbcTemplate.queryForMap(
            "select valor, status from lancamento_frete where id = ?", lancamentoId);
    assertThat(((BigDecimal) original.get("valor")).setScale(2)).isEqualByComparingTo("6.00");

    Integer ajustes =
        jdbcTemplate.queryForObject(
            "select count(*) from ajuste_lancamento_frete where lancamento_id = ?",
            Integer.class,
            lancamentoId);
    assertThat(ajustes).isEqualTo(1);
  }

  private EarningsResponse earnings(RegisteredTestUser courier) {
    return restTemplate
        .exchange(
            baseUrl("/me/earnings"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            EarningsResponse.class)
        .getBody();
  }

  private ResponseEntity<SettlementResponse> settle(RegisteredTestUser courier, UUID lancamentoId) {
    return restTemplate.exchange(
        baseUrl("/me/earnings/settlements"),
        HttpMethod.POST,
        authed(login(courier).accessToken(), new SettlementRequest(List.of(lancamentoId))),
        SettlementResponse.class);
  }

  private ResponseEntity<ErrorResponse> settleComErro(
      RegisteredTestUser courier, UUID lancamentoId) {
    return restTemplate.exchange(
        baseUrl("/me/earnings/settlements"),
        HttpMethod.POST,
        authed(login(courier).accessToken(), new SettlementRequest(List.of(lancamentoId))),
        ErrorResponse.class);
  }

  private UUID lancamentoDoPedido(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select id from lancamento_frete where pedido_id = ?", UUID.class, pedidoId);
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
