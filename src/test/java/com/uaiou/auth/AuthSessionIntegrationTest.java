package com.uaiou.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.auth.service.JwtService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-03.3 a RF-03.8 — critérios de aceite 3 a 7 de T-03. */
class AuthSessionIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private JwtService jwtService;

  @Test
  void passwordGrantSucceedsForActiveUserAndAccessTokenCarriesCorrectClaims() {
    RegisteredTestUser user = registerAndActivateCourier();

    SessionResponse session = login(user);

    assertThat(session.accessToken()).isNotBlank();
    assertThat(session.refreshToken()).isNotBlank();
    assertThat(session.expiresIn()).isPositive();
    assertThat(session.user().id()).isEqualTo(user.id());
    assertThat(session.user().status()).isEqualTo(UserStatus.ACTIVE);
    assertThat(session.links()).containsKeys("profile", "logout");

    Optional<AuthenticatedUser> decoded = jwtService.parse(session.accessToken());
    assertThat(decoded).isPresent();
    assertThat(decoded.get().userId()).isEqualTo(user.id());
    assertThat(decoded.get().role()).isEqualTo(Role.COURIER);
    assertThat(decoded.get().status()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void wrongPasswordIsRejected() {
    RegisteredTestUser user = registerAndActivateCourier();

    SessionRequest request =
        new SessionRequest(
            "password", user.login(), "senha-errada-completamente", null, null, user.role());
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error().code()).isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void nonexistentLoginFailsWithTheSameGenericCredentialErrorAsWrongPassword() {
    SessionRequest request =
        new SessionRequest(
            "password",
            "login-que-nao-existe-" + uniqueSuffix(),
            "qualquer-senha",
            null,
            null,
            Role.COURIER);
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error().code()).isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void unsupportedGrantTypeIsRejected() {
    SessionRequest request = new SessionRequest("carrier-pigeon", null, null, null, null, null);
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("UNSUPPORTED_GRANT_TYPE");
  }

  @Test
  void roleMismatchIsRejected() {
    RegisteredTestUser courier = registerAndActivateCourier();

    SessionRequest request =
        new SessionRequest(
            "password", courier.login(), courier.password(), null, null, Role.MERCHANT);
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("ROLE_MISMATCH");
    assertThat(response.getBody().error().rule()).isEqualTo("RF-03.5");
  }

  @Test
  void bannedUserIsRejectedWithReason() {
    RegisteredTestUser user = registerAndActivateCourier();
    moderation.ban(user.id(), "Fraude confirmada em auditoria.");

    SessionRequest request =
        new SessionRequest("password", user.login(), user.password(), null, null, user.role());
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("ACCOUNT_BANNED");
    assertThat(response.getBody().error().details())
        .containsEntry("reason", "Fraude confirmada em auditoria.");
  }

  @Test
  void suspendedUserIsRejectedWithReasonAndDeadline() {
    RegisteredTestUser user = registerAndActivateCourier();
    Instant until = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    moderation.suspend(user.id(), "Reincidência de cancelamentos.", until);

    SessionRequest request =
        new SessionRequest("password", user.login(), user.password(), null, null, user.role());
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("ACCOUNT_SUSPENDED");
    assertThat(response.getBody().error().details())
        .containsEntry("reason", "Reincidência de cancelamentos.");
    assertThat(response.getBody().error().details()).containsEntry("until", until.toString());
  }

  @Test
  void refreshGrantRotatesTheTokenAndTheOldOneStopsWorking() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse original = login(user);

    SessionRequest refreshRequest =
        new SessionRequest("refresh", null, null, null, original.refreshToken(), null);
    ResponseEntity<SessionResponse> refreshed =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"), refreshRequest, SessionResponse.class);

    assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(refreshed.getBody().refreshToken()).isNotEqualTo(original.refreshToken());
    assertThat(refreshed.getBody().accessToken()).isNotEqualTo(original.accessToken());

    // O par antigo não é mais aceito — usá-lo de novo cai no caminho de reuso (critério de aceite
    // 7).
    ResponseEntity<ErrorResponse> reuse =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), refreshRequest, ErrorResponse.class);
    assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(reuse.getBody().error().code()).isEqualTo("REFRESH_TOKEN_REUSED");
  }

  @Test
  void reusingARevokedRefreshTokenAlsoInvalidatesTheTokenIssuedFromIt() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse original = login(user);
    String firstRefreshToken = original.refreshToken();

    SessionRequest firstRefreshRequest =
        new SessionRequest("refresh", null, null, null, firstRefreshToken, null);
    ResponseEntity<SessionResponse> secondSession =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"), firstRefreshRequest, SessionResponse.class);
    String secondRefreshToken = secondSession.getBody().refreshToken();

    // Reusa o primeiro refresh (já revogado pela rotação acima) — sinal de vazamento, revoga a
    // família inteira.
    ResponseEntity<ErrorResponse> reuseResponse =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"), firstRefreshRequest, ErrorResponse.class);
    assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(reuseResponse.getBody().error().code()).isEqualTo("REFRESH_TOKEN_REUSED");

    // O segundo refresh, nascido da mesma família, também deixou de funcionar.
    SessionRequest secondRefreshRequest =
        new SessionRequest("refresh", null, null, null, secondRefreshToken, null);
    ResponseEntity<ErrorResponse> secondAttempt =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"), secondRefreshRequest, ErrorResponse.class);
    assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void unknownRefreshTokenIsRejected() {
    SessionRequest request =
        new SessionRequest("refresh", null, null, null, "rt_token-que-nunca-existiu", null);
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error().code()).isEqualTo("INVALID_REFRESH_TOKEN");
  }
}
