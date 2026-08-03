package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.credits.dto.CreatePlanRequest;
import com.uaiou.credits.dto.PlanSummary;
import com.uaiou.credits.dto.UpdatePlanRequest;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-09.1 — CRUD mínimo do catálogo de planos, restrito ao admin. */
class PlanCatalogIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void createsAndListsAPlan() {
    AdminSession admin = loginAdmin();

    ResponseEntity<PlanSummary> created =
        restTemplate.exchange(
            baseUrl("/admin/plans"),
            HttpMethod.POST,
            authed(
                admin.accessToken(),
                new CreatePlanRequest("Essencial Teste", 150, Money.of("99.90"))),
            PlanSummary.class);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody().monthlyCredits()).isEqualTo(150);
    assertThat(created.getBody().active()).isTrue();

    PageResponse<PlanSummary> page =
        restTemplate
            .exchange(
                baseUrl("/admin/plans"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin.accessToken())),
                new ParameterizedTypeReference<PageResponse<PlanSummary>>() {})
            .getBody();
    assertThat(page.data()).anyMatch(p -> p.id().equals(created.getBody().id()));
  }

  @Test
  void updatesOnlyTheFieldsSent() {
    AdminSession admin = loginAdmin();
    PlanSummary created =
        restTemplate
            .exchange(
                baseUrl("/admin/plans"),
                HttpMethod.POST,
                authed(
                    admin.accessToken(), new CreatePlanRequest("Plano X", 100, Money.of("50.00"))),
                PlanSummary.class)
            .getBody();

    ResponseEntity<PlanSummary> updated =
        restTemplate.exchange(
            baseUrl("/admin/plans/" + created.id()),
            HttpMethod.PUT,
            authed(admin.accessToken(), new UpdatePlanRequest(null, null, null, false)),
            PlanSummary.class);

    assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(updated.getBody().active()).isFalse();
    assertThat(updated.getBody().name()).isEqualTo("Plano X");
    assertThat(updated.getBody().monthlyCredits()).isEqualTo(100);
  }

  @Test
  void updatingAnUnknownPlanIsNotFound() {
    AdminSession admin = loginAdmin();

    ResponseEntity<Object> response =
        restTemplate.exchange(
            baseUrl("/admin/plans/" + UUID.randomUUID()),
            HttpMethod.PUT,
            authed(admin.accessToken(), new UpdatePlanRequest("Novo nome", null, null, null)),
            Object.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
