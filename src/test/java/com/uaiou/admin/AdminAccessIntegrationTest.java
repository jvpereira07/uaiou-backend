package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * T-07 é o primeiro módulo com rotas restritas por papel — cobre o {@link
 * com.uaiou.admin.web.AdminAccessGuard}.
 */
class AdminAccessIntegrationTest extends AbstractAuthIntegrationTest {

  private static final List<String> ADMIN_ROUTES =
      List.of(
          "/admin/registrations?status=pending",
          "/admin/couriers",
          "/admin/merchants",
          "/admin/audit-logs");

  @Test
  void nonAdminGetsForbiddenOnEveryAdminRoute() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();

    for (String route : ADMIN_ROUTES) {
      ResponseEntity<ErrorResponse> response =
          restTemplate.exchange(
              baseUrl(route),
              HttpMethod.GET,
              new HttpEntity<>(authHeaders(token)),
              ErrorResponse.class);
      assertThat(response.getStatusCode()).as(route).isEqualTo(HttpStatus.FORBIDDEN);
      assertThat(response.getBody().error().code()).as(route).isEqualTo("ADMIN_ONLY");
    }
  }

  @Test
  void missingTokenGetsUnauthorizedOnAnAdminRoute() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/admin/couriers"), HttpMethod.GET, HttpEntity.EMPTY, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
