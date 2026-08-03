package com.uaiou.credits;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.dto.AssignPlanRequest;
import com.uaiou.credits.dto.CreatePlanRequest;
import com.uaiou.credits.dto.CreditTransactionSummary;
import com.uaiou.credits.dto.MyCreditsResponse;
import com.uaiou.credits.dto.PlanSummary;
import com.uaiou.credits.service.CreditWalletService;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-09.8/RF-09.9 — critério de aceite 7 de T-09. */
class MyCreditsIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private CreditWalletService creditWalletService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void freshMerchantHasZeroBalanceAndNoSubscription() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    String token = login(merchant).accessToken();

    MyCreditsResponse response = getCredits(token);

    assertThat(response.creditsBalance()).isZero();
    assertThat(response.subscription()).isNull();
  }

  @Test
  void consumedThisCycleReflectsOnlyTransactionsSinceTheCurrentCycleStarted() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    String token = login(merchant).accessToken();
    PlanSummary plan = createPlan(admin, 100);
    assign(admin, merchant.id(), plan.id());

    // Consumo "do ciclo anterior" — carimbado no passado direto via SQL, não deveria contar.
    UUID pedidoAntigo = createBarePedido(merchant.id());
    jdbcTemplate.update(
        "insert into transacao_credito (id, estabelecimento_id, tipo, quantidade, pedido_id, criado_em) "
            + "values (?, ?, 'consumo_postagem', -10, ?, ?)",
        UuidV7.next(),
        merchant.id(),
        pedidoAntigo,
        Timestamp.from(Instant.now().minus(40, ChronoUnit.DAYS)));

    // Consumo do ciclo atual.
    UUID pedidoAtual = createBarePedido(merchant.id());
    creditWalletService.consumirParaPedido(merchant.id(), 5, pedidoAtual);

    MyCreditsResponse response = getCredits(token);

    assertThat(response.subscription().consumedThisCycle()).isEqualTo(5);
    assertThat(response.creditsBalance()).isEqualTo(100 - 5);
  }

  @Test
  void transactionsExtractListsWhatHappened() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    String token = login(merchant).accessToken();
    PlanSummary plan = createPlan(admin, 100);
    assign(admin, merchant.id(), plan.id());

    PageResponse<CreditTransactionSummary> page = getTransactions(token);

    assertThat(page.data()).hasSize(1);
    assertThat(page.data().get(0).quantity()).isEqualTo(100);
  }

  @Test
  void nonMerchantCannotReadCredits() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/me/credits"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("MERCHANT_ONLY");
  }

  private MyCreditsResponse getCredits(String token) {
    return restTemplate
        .exchange(
            baseUrl("/me/credits"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            MyCreditsResponse.class)
        .getBody();
  }

  private PageResponse<CreditTransactionSummary> getTransactions(String token) {
    return restTemplate
        .exchange(
            baseUrl("/me/credits/transactions"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(token)),
            new ParameterizedTypeReference<PageResponse<CreditTransactionSummary>>() {})
        .getBody();
  }

  private PlanSummary createPlan(AdminSession admin, int monthlyCredits) {
    return restTemplate
        .exchange(
            baseUrl("/admin/plans"),
            HttpMethod.POST,
            authed(
                admin.accessToken(),
                new CreatePlanRequest("Plano de teste", monthlyCredits, Money.of("50.00"))),
            PlanSummary.class)
        .getBody();
  }

  private void assign(AdminSession admin, UUID merchantId, UUID planId) {
    restTemplate.exchange(
        baseUrl("/admin/merchants/" + merchantId + "/plan"),
        HttpMethod.PUT,
        authed(admin.accessToken(), new AssignPlanRequest(planId)),
        Object.class);
  }

  /**
   * {@code numero} usa {@code UUID.randomUUID()} (v4), não o {@code id} v7: dois pedidos criados no
   * mesmo teste caem quase sempre no mesmo milissegundo, e os primeiros bits de um v7 são timestamp
   * — um substring dele colidiria em {@code numero}.
   */
  private UUID createBarePedido(UUID estabelecimentoId) {
    UUID id = UuidV7.next();
    jdbcTemplate.update(
        "insert into pedido (id, numero, estabelecimento_id, frete_proposto, creditos_consumidos, "
            + "dest_bairro, dest_rua, dest_numero, dest_lat, dest_long, recebedor_nome) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        "P-" + UUID.randomUUID().toString().substring(0, 8),
        estabelecimentoId,
        new BigDecimal("6.00"),
        5,
        "Centro",
        "Rua de Teste",
        "100",
        new BigDecimal("-19.917299"),
        new BigDecimal("-43.934559"),
        "Cliente de Teste");
    return id;
  }
}
