package com.uaiou.credits;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.credits.entity.CarteiraCreditos;
import com.uaiou.credits.repository.CarteiraCreditosRepository;
import com.uaiou.credits.service.SubscriptionRenewalJob;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-09.4 — critério de aceite 3 de T-09. */
class SubscriptionRenewalJobIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private SubscriptionRenewalJob subscriptionRenewalJob;
  @Autowired private CarteiraCreditosRepository carteiraCreditosRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void aDueCycleIsCreditedOnceEvenIfTheJobRunsTwice() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    UUID planId = createPlan(100);
    UUID assinaturaId = createDueSubscription(merchant.id(), planId);

    subscriptionRenewalJob.renovarCiclosVencidos();
    subscriptionRenewalJob.renovarCiclosVencidos();

    assertThat(saldoDe(merchant.id())).isEqualTo(100);
    Integer proximaRenovacaoNoFuturo =
        jdbcTemplate.queryForObject(
            "select case when proxima_renovacao > current_date then 1 else 0 end from assinatura where id = ?",
            Integer.class,
            assinaturaId);
    assertThat(proximaRenovacaoNoFuturo).isEqualTo(1);
  }

  @Test
  void aSubscriptionNotYetDueIsUntouched() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    UUID planId = createPlan(100);
    createFutureSubscription(merchant.id(), planId);

    subscriptionRenewalJob.renovarCiclosVencidos();

    assertThat(saldoDe(merchant.id())).isZero();
  }

  private int saldoDe(UUID estabelecimentoId) {
    return carteiraCreditosRepository
        .findById(estabelecimentoId)
        .map(CarteiraCreditos::getSaldoCreditos)
        .orElse(0);
  }

  private UUID createPlan(int cota) {
    UUID id = UuidV7.next();
    jdbcTemplate.update(
        "insert into plano (id, nome, cota_mensal_creditos, preco) values (?, ?, ?, ?)",
        id,
        "Plano de teste " + id,
        cota,
        Money.of("50.00").amount());
    return id;
  }

  private UUID createDueSubscription(UUID estabelecimentoId, UUID planId) {
    UUID id = UuidV7.next();
    jdbcTemplate.update(
        "insert into assinatura (id, estabelecimento_id, plano_id, status, inicio, proxima_renovacao) "
            + "values (?, ?, ?, 'ativa', ?, ?)",
        id,
        estabelecimentoId,
        planId,
        LocalDate.now().minusMonths(1),
        LocalDate.now());
    return id;
  }

  private UUID createFutureSubscription(UUID estabelecimentoId, UUID planId) {
    UUID id = UuidV7.next();
    jdbcTemplate.update(
        "insert into assinatura (id, estabelecimento_id, plano_id, status, inicio, proxima_renovacao) "
            + "values (?, ?, ?, 'ativa', ?, ?)",
        id,
        estabelecimentoId,
        planId,
        LocalDate.now(),
        LocalDate.now().plusDays(10));
    return id;
  }
}
