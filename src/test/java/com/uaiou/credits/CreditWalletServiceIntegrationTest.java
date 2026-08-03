package com.uaiou.credits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uaiou.admin.dto.AssignPlanRequest;
import com.uaiou.credits.dto.CreatePlanRequest;
import com.uaiou.credits.dto.PlanSummary;
import com.uaiou.credits.entity.CarteiraCreditos;
import com.uaiou.credits.repository.CarteiraCreditosRepository;
import com.uaiou.credits.service.CreditWalletService;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RF-09.6/RF-09.11 — critérios de aceite 4, 5, 6 de T-09. Chama {@link CreditWalletService}
 * diretamente (sem rota HTTP): a rota que consome crédito ao publicar pedido é do T-11, ainda não
 * construída — este teste garante a mecânica que T-11 vai reusar (mesmo padrão de T-05/T-06
 * validando um serviço reusável antes da rota que o consome existir).
 */
class CreditWalletServiceIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private CreditWalletService creditWalletService;
  @Autowired private CarteiraCreditosRepository carteiraCreditosRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void consumingDebitsTheWalletAndRecordsTheOrder() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    grantCredits(merchant, 100);
    UUID pedidoId = createBarePedido(merchant.id(), 1);

    creditWalletService.consumirParaPedido(merchant.id(), 1, pedidoId);

    assertThat(saldoDe(merchant.id())).isEqualTo(99);
  }

  @Test
  void consumingWithoutEnoughCreditsFailsAndCreatesNoOrderSideEffect() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    grantCredits(merchant, 1);
    UUID pedidoId = createBarePedido(merchant.id(), 5);

    assertThatThrownBy(() -> creditWalletService.consumirParaPedido(merchant.id(), 5, pedidoId))
        .isInstanceOf(BusinessRuleException.class)
        .satisfies(
            e -> {
              BusinessRuleException ex = (BusinessRuleException) e;
              assertThat(ex.code()).isEqualTo("INSUFFICIENT_CREDITS");
              assertThat(ex.rule()).isEqualTo("RN-05.1");
            });

    // Saldo não muda e nenhum lançamento de consumo foi gravado para este pedido.
    assertThat(saldoDe(merchant.id())).isEqualTo(1);
    Integer transacoes =
        jdbcTemplate.queryForObject(
            "select count(*) from transacao_credito where pedido_id = ?", Integer.class, pedidoId);
    assertThat(transacoes).isZero();
  }

  @Test
  void concurrentConsumptionWithOnlyEnoughBalanceForOneSucceedsExactlyOnce() throws Exception {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    grantCredits(merchant, 1);
    UUID pedidoA = createBarePedido(merchant.id(), 1);
    UUID pedidoB = createBarePedido(merchant.id(), 1);

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch startGate = new CountDownLatch(2);
    CountDownLatch goGate = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger failures = new AtomicInteger();

    List<Future<?>> futures = new ArrayList<>();
    for (UUID pedidoId : List.of(pedidoA, pedidoB)) {
      futures.add(
          pool.submit(
              () -> {
                startGate.countDown();
                try {
                  goGate.await();
                  creditWalletService.consumirParaPedido(merchant.id(), 1, pedidoId);
                  successes.incrementAndGet();
                } catch (BusinessRuleException e) {
                  failures.incrementAndGet();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }));
    }

    startGate.await(5, TimeUnit.SECONDS);
    goGate.countDown();
    for (Future<?> future : futures) {
      future.get(10, TimeUnit.SECONDS);
    }
    pool.shutdown();

    assertThat(successes.get()).isEqualTo(1);
    assertThat(failures.get()).isEqualTo(1);
    assertThat(saldoDe(merchant.id())).isZero();
  }

  /** Concede créditos pelo fluxo real (admin cria plano + atribui) — {@code assinatura_id} é FK. */
  private void grantCredits(RegisteredTestUser merchant, int quantidade) {
    AdminSession admin = loginAdmin();
    PlanSummary plan =
        restTemplate
            .exchange(
                baseUrl("/admin/plans"),
                HttpMethod.POST,
                authed(
                    admin.accessToken(),
                    new CreatePlanRequest("Plano de teste", quantidade, Money.of("10.00"))),
                PlanSummary.class)
            .getBody();
    restTemplate.exchange(
        baseUrl("/admin/merchants/" + merchant.id() + "/plan"),
        HttpMethod.PUT,
        authed(admin.accessToken(), new AssignPlanRequest(plan.id())),
        Object.class);
  }

  private int saldoDe(UUID estabelecimentoId) {
    return carteiraCreditosRepository
        .findById(estabelecimentoId)
        .map(CarteiraCreditos::getSaldoCreditos)
        .orElse(0);
  }

  /**
   * Linha mínima válida de {@code pedido} — a rota real de publicação é do T-11. {@code numero} usa
   * {@code UUID.randomUUID()} (v4), não o {@code id} v7: dois pedidos criados no mesmo teste caem
   * quase sempre no mesmo milissegundo, e os primeiros bits de um v7 são timestamp — um substring
   * dele colidiria em {@code numero} (mesmo motivo de {@code UserModerationTestFixtures}).
   */
  private UUID createBarePedido(UUID estabelecimentoId, int creditosConsumidos) {
    UUID id = UuidV7.next();
    jdbcTemplate.update(
        "insert into pedido (id, numero, estabelecimento_id, frete_proposto, creditos_consumidos, "
            + "dest_bairro, dest_rua, dest_numero, dest_lat, dest_long, recebedor_nome) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        "P-" + UUID.randomUUID().toString().substring(0, 8),
        estabelecimentoId,
        new BigDecimal("6.00"),
        creditosConsumidos,
        "Centro",
        "Rua de Teste",
        "100",
        new BigDecimal("-19.917299"),
        new BigDecimal("-43.934559"),
        "Cliente de Teste");
    return id;
  }
}
