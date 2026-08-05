package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.dto.FinancialAdjustmentRequest;
import com.uaiou.admin.dto.FinancialAdjustmentResponse;
import com.uaiou.credits.dto.MyCreditsResponse;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-09.10 — critério de aceite 8 de T-09. */
class FinancialAdjustmentIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void creditsAdjustmentAddsCreditsAndIsAudited() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();

    ResponseEntity<FinancialAdjustmentResponse> response =
        restTemplate.exchange(
            baseUrl("/admin/financial-adjustments"),
            HttpMethod.POST,
            authed(
                admin.accessToken(),
                new FinancialAdjustmentRequest(
                    "credits_adjustment",
                    merchant.id(),
                    50,
                    "Recarga paga por Pix e não creditada",
                    new FinancialAdjustmentRequest.ReferenceRef(
                        "support_ticket", UUID.randomUUID()),
                    null)),
            FinancialAdjustmentResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(getBalance(merchant)).isEqualTo(50);
  }

  @Test
  void negativeAdjustmentDebitsCredits() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    applyAdjustment(admin, merchant.id(), 50);

    applyAdjustment(admin, merchant.id(), -20);

    assertThat(getBalance(merchant)).isEqualTo(30);
  }

  @Test
  void adjustmentWithoutReferenceIsRejectedAsABusinessRule() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/admin/financial-adjustments"),
            HttpMethod.POST,
            authed(
                admin.accessToken(),
                new FinancialAdjustmentRequest(
                    "credits_adjustment", merchant.id(), 50, "Sem referência", null, null)),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("MISSING_REFERENCE");
    assertThat(response.getBody().error().rule()).isEqualTo("RN-13.2");
  }

  @Test
  void unsupportedAdjustmentTypeIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/admin/financial-adjustments"),
            HttpMethod.POST,
            authed(
                admin.accessToken(),
                new FinancialAdjustmentRequest(
                    "balance_credit",
                    merchant.id(),
                    50,
                    "Não suportado ainda",
                    new FinancialAdjustmentRequest.ReferenceRef(
                        "support_ticket", UUID.randomUUID()),
                    null)),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("UNSUPPORTED_ADJUSTMENT_TYPE");
  }

  private void applyAdjustment(AdminSession admin, UUID merchantId, int amount) {
    restTemplate.exchange(
        baseUrl("/admin/financial-adjustments"),
        HttpMethod.POST,
        authed(
            admin.accessToken(),
            new FinancialAdjustmentRequest(
                "credits_adjustment",
                merchantId,
                amount,
                "Ajuste de teste",
                new FinancialAdjustmentRequest.ReferenceRef("support_ticket", UUID.randomUUID()),
                null)),
        FinancialAdjustmentResponse.class);
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
