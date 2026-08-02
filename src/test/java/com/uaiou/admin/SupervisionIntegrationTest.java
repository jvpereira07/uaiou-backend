package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.dto.AdminUserDetail;
import com.uaiou.admin.dto.CourierSummary;
import com.uaiou.admin.dto.CreateSanctionRequest;
import com.uaiou.admin.dto.MerchantSummary;
import com.uaiou.admin.dto.SanctionSummary;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.SanctionType;
import com.uaiou.users.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-07.8 — listagens de supervisão e dossiê completo do usuário. */
class SupervisionIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void couriersListingIncludesTheRegisteredCourier() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();

    PageResponse<CourierSummary> page = getCouriers("/admin/couriers", admin.accessToken());

    CourierSummary entry =
        page.data().stream().filter(c -> c.id().equals(courier.id())).findFirst().orElseThrow();
    assertThat(entry.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(entry.vehicleType()).isEqualTo("MOTORCYCLE");
  }

  @Test
  void couriersListingFiltersByStatus() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser pendingCourier = registerCourier();
    registerAndActivateCourier();

    PageResponse<CourierSummary> page =
        getCouriers("/admin/couriers?status=pending", admin.accessToken());

    assertThat(page.data()).anyMatch(c -> c.id().equals(pendingCourier.id()));
    assertThat(page.data()).allSatisfy(c -> assertThat(c.status()).isEqualTo(UserStatus.PENDING));
  }

  @Test
  void merchantsListingIncludesTheRegisteredMerchant() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser merchant = registerAndActivateMerchant();

    PageResponse<MerchantSummary> page = getMerchants("/admin/merchants", admin.accessToken());

    assertThat(page.data()).anyMatch(m -> m.id().equals(merchant.id()));
  }

  @Test
  void userDetailIncludesProfileDocumentsAndSanctionHistory() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();
    applySanction(
        admin.accessToken(),
        courier.id(),
        new CreateSanctionRequest(
            SanctionType.SUSPENSION,
            "Histórico de teste.",
            Instant.now().plus(1, ChronoUnit.DAYS)));

    ResponseEntity<AdminUserDetail> response =
        restTemplate.exchange(
            baseUrl("/admin/users/" + courier.id()),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(admin.accessToken())),
            AdminUserDetail.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    AdminUserDetail detail = response.getBody();
    assertThat(detail.profile().cpf()).isNotBlank();
    assertThat(detail.sanctions()).hasSize(1);
    assertThat(detail.sanctions().get(0).reason()).isEqualTo("Histórico de teste.");
    assertThat(detail.status()).isEqualTo(UserStatus.SUSPENDED);
  }

  private ResponseEntity<SanctionSummary> applySanction(
      String adminToken, UUID userId, CreateSanctionRequest request) {
    return restTemplate.exchange(
        baseUrl("/admin/users/" + userId + "/sanctions"),
        HttpMethod.POST,
        authed(adminToken, request),
        SanctionSummary.class);
  }

  private PageResponse<CourierSummary> getCouriers(String path, String adminToken) {
    return restTemplate
        .exchange(
            baseUrl(path),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(adminToken)),
            new ParameterizedTypeReference<PageResponse<CourierSummary>>() {})
        .getBody();
  }

  private PageResponse<MerchantSummary> getMerchants(String path, String adminToken) {
    return restTemplate
        .exchange(
            baseUrl(path),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(adminToken)),
            new ParameterizedTypeReference<PageResponse<MerchantSummary>>() {})
        .getBody();
  }
}
