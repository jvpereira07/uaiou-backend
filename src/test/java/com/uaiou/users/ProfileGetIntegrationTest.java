package com.uaiou.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.dto.MeResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-04.1, RF-04.2, RF-04.5, RF-04.7 — critérios de aceite 1 e 2 de T-04. */
class ProfileGetIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void activeCourierGetsFullProfileAndOperationLinksButNotDocuments() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse session = login(user);

    ResponseEntity<MeResponse> response = getMe(session.accessToken());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MeResponse body = response.getBody();
    assertThat(body.role()).isEqualTo(Role.COURIER);
    assertThat(body.status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(body.profile().cpf()).isNotBlank();
    assertThat(body.profile().vehicleType()).isEqualTo("MOTORCYCLE");
    assertThat(body.profile().completedDeliveries()).isEqualTo(0);
    assertThat(body.links())
        .containsKeys("availability", "location", "openOrders", "wallet", "score");
    assertThat(body.links()).doesNotContainKey("documents");
    assertThat(body.pendingFields()).isNull();
  }

  @Test
  void pendingCourierGetsDocumentsLinkButNotAvailability() {
    RegisteredTestUser user = registerCourier();
    SessionResponse session = login(user);

    ResponseEntity<MeResponse> response = getMe(session.accessToken());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MeResponse body = response.getBody();
    assertThat(body.status()).isEqualTo(UserStatus.PENDING);
    assertThat(body.links()).containsKey("documents");
    assertThat(body.links()).doesNotContainKeys("availability", "location", "openOrders");
  }

  @Test
  void activeMerchantGetsOrdersCreditsAndBlockedCouriersLinks() {
    RegisteredTestUser user = registerAndActivateMerchant();
    SessionResponse session = login(user);

    ResponseEntity<MeResponse> response = getMe(session.accessToken());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MeResponse body = response.getBody();
    assertThat(body.role()).isEqualTo(Role.MERCHANT);
    assertThat(body.profile().cnpj()).isNotBlank();
    assertThat(body.profile().businessName()).isNotBlank();
    assertThat(body.links()).containsKeys("orders", "credits", "blockedCouriers");
    assertThat(body.links()).doesNotContainKeys("availability", "location", "wallet");
  }

  @Test
  void suspendedUserGetsOnlySelfAndSupportLinks() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse session = login(user);
    moderation.suspend(user.id(), "Verificação de rotina.", Instant.now().plus(1, ChronoUnit.DAYS));

    ResponseEntity<MeResponse> response = getMe(session.accessToken());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    MeResponse body = response.getBody();
    assertThat(body.status()).isEqualTo(UserStatus.SUSPENDED);
    assertThat(body.links()).containsOnlyKeys("self", "support");
  }

  @Test
  void noSensitiveFieldOrPasswordHashEverAppearsInTheRawJson() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse session = login(user);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(session.accessToken());
    ResponseEntity<String> response =
        restTemplate.exchange(
            baseUrl("/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String json = response.getBody();
    assertThat(json).doesNotContainIgnoringCase("senhaHash");
    assertThat(json).doesNotContainIgnoringCase("senha_hash");
    assertThat(json).doesNotContainIgnoringCase("googleId");
    assertThat(json).doesNotContainIgnoringCase("google_id");
  }

  @Test
  void unauthenticatedGetIsRejected() {
    ResponseEntity<String> response = restTemplate.getForEntity(baseUrl("/me"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private ResponseEntity<MeResponse> getMe(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return restTemplate.exchange(
        baseUrl("/me"), HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);
  }
}
