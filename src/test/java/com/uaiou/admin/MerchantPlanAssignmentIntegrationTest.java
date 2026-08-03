package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.dto.AssignPlanRequest;
import com.uaiou.admin.dto.AuditLogEntry;
import com.uaiou.credits.dto.CreatePlanRequest;
import com.uaiou.credits.dto.MyCreditsResponse;
import com.uaiou.credits.dto.PlanSummary;
import com.uaiou.credits.dto.SubscriptionSummary;
import com.uaiou.shared.error.ErrorResponse;
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
import org.springframework.web.util.UriComponentsBuilder;

/** RF-09.2/RF-09.3 — critérios de aceite 1 e 2 de T-09. */
class MerchantPlanAssignmentIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void assigningAPlanToAFreshMerchantCreditsTheFullQuota() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    PlanSummary plan = createPlan(admin, "Essencial", 100);

    ResponseEntity<SubscriptionSummary> response = assign(admin, merchant.id(), plan.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().monthlyCredits()).isEqualTo(100);
    assertThat(getBalance(merchant)).isEqualTo(100);
  }

  @Test
  void reassigningTheSamePlanDoesNotCreditAgain() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    PlanSummary plan = createPlan(admin, "Essencial", 100);
    assign(admin, merchant.id(), plan.id());

    assign(admin, merchant.id(), plan.id());

    assertThat(getBalance(merchant)).isEqualTo(100);
  }

  @Test
  void switchingToABiggerPlanMidCycleCreditsOnlyTheDifferential() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    PlanSummary plan100 = createPlan(admin, "Essencial", 100);
    PlanSummary plan300 = createPlan(admin, "Profissional", 300);
    assign(admin, merchant.id(), plan100.id());

    assign(admin, merchant.id(), plan300.id());

    assertThat(getBalance(merchant)).isEqualTo(200 + 100);
  }

  @Test
  void switchingToASmallerPlanMidCycleDoesNotRemoveCredits() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    PlanSummary plan300 = createPlan(admin, "Profissional", 300);
    PlanSummary plan100 = createPlan(admin, "Essencial", 100);
    assign(admin, merchant.id(), plan300.id());

    ResponseEntity<SubscriptionSummary> response = assign(admin, merchant.id(), plan100.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getBalance(merchant)).isEqualTo(300);
  }

  @Test
  void everyAssignmentGeneratesExactlyOneAuditRow() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    PlanSummary plan = createPlan(admin, "Essencial", 100);

    assign(admin, merchant.id(), plan.id());

    String url =
        UriComponentsBuilder.fromUriString(baseUrl("/admin/audit-logs"))
            .queryParam("referenceId", merchant.id())
            .toUriString();
    PageResponse<AuditLogEntry> page =
        restTemplate
            .exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin.accessToken())),
                new ParameterizedTypeReference<PageResponse<AuditLogEntry>>() {})
            .getBody();
    assertThat(page.data()).hasSize(1);
    assertThat(page.data().get(0).action()).isEqualTo("atribuir_plano");
  }

  @Test
  void assigningAnUnknownPlanIsNotFound() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/admin/merchants/" + merchant.id() + "/plan"),
            HttpMethod.PUT,
            authed(admin.accessToken(), new AssignPlanRequest(UUID.randomUUID())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error().code()).isEqualTo("PLAN_NOT_FOUND");
  }

  @Test
  void assigningToAnUnknownMerchantIsNotFound() {
    AdminSession admin = loginAdmin();
    PlanSummary plan = createPlan(admin, "Essencial", 100);

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/admin/merchants/" + UUID.randomUUID() + "/plan"),
            HttpMethod.PUT,
            authed(admin.accessToken(), new AssignPlanRequest(plan.id())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error().code()).isEqualTo("MERCHANT_NOT_FOUND");
  }

  @Test
  void nonAdminCannotAssignPlans() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    String merchantToken = login(merchant).accessToken();

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/admin/merchants/" + merchant.id() + "/plan"),
            HttpMethod.PUT,
            authed(merchantToken, new AssignPlanRequest(UUID.randomUUID())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("ADMIN_ONLY");
  }

  private PlanSummary createPlan(AdminSession admin, String name, int monthlyCredits) {
    return restTemplate
        .exchange(
            baseUrl("/admin/plans"),
            HttpMethod.POST,
            authed(
                admin.accessToken(),
                new CreatePlanRequest(name, monthlyCredits, Money.of("99.90"))),
            PlanSummary.class)
        .getBody();
  }

  private ResponseEntity<SubscriptionSummary> assign(
      AdminSession admin, UUID merchantId, UUID planId) {
    return restTemplate.exchange(
        baseUrl("/admin/merchants/" + merchantId + "/plan"),
        HttpMethod.PUT,
        authed(admin.accessToken(), new AssignPlanRequest(planId)),
        SubscriptionSummary.class);
  }

  private int getBalance(RegisteredTestUser merchant) {
    String merchantToken = login(merchant).accessToken();
    MyCreditsResponse response =
        restTemplate
            .exchange(
                baseUrl("/me/credits"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(merchantToken)),
                MyCreditsResponse.class)
            .getBody();
    return response.creditsBalance();
  }
}
