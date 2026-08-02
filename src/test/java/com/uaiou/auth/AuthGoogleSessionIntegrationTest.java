package com.uaiou.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.auth.service.GoogleIdTokenVerifierPort;
import com.uaiou.auth.service.GoogleIdentity;
import com.uaiou.shared.error.ErrorResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * RF-03.6. {@link GoogleIdTokenVerifierPort} existe como porta justamente para isto: um token real
 * do Google não pode ser forjado em CI, então o adaptador real é trocado por um fake controlável
 * ({@link FakeGoogleIdTokenVerifier}), sem tocar em {@code SessionService}.
 */
@Import(AuthGoogleSessionIntegrationTest.FakeGoogleConfig.class)
class AuthGoogleSessionIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private FakeGoogleIdTokenVerifier fakeVerifier;

  @Test
  void invalidGoogleTokenIsRejected() {
    fakeVerifier.nextResult = Optional.empty();

    SessionRequest request =
        new SessionRequest(
            "google", null, null, "token-invalido", null, com.uaiou.users.Role.COURIER);
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error().code()).isEqualTo("INVALID_GOOGLE_TOKEN");
  }

  @Test
  void verifiedIdentityWithNoLinkedAccountIsRejectedWithoutCreatingOne() {
    fakeVerifier.nextResult =
        Optional.of(
            new GoogleIdentity(
                "google-" + UUID.randomUUID(),
                "ninguem-" + UUID.randomUUID() + "@uaiou.test",
                true));

    SessionRequest request =
        new SessionRequest(
            "google", null, null, "token-sem-conta", null, com.uaiou.users.Role.COURIER);
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error().code()).isEqualTo("GOOGLE_ACCOUNT_NOT_LINKED");
  }

  @Test
  void unverifiedEmailNeverResolvesAnAccountByFallback() {
    RegisteredTestUser user = registerAndActivateCourier();
    fakeVerifier.nextResult =
        Optional.of(
            new GoogleIdentity("google-" + UUID.randomUUID(), user.login() + "@uaiou.test", false));

    SessionRequest request =
        new SessionRequest("google", null, null, "token-email-nao-verificado", null, user.role());
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error().code()).isEqualTo("GOOGLE_ACCOUNT_NOT_LINKED");
  }

  @Test
  void verifiedEmailFallbackLinksTheAccountAndTheNextLoginResolvesByGoogleIdAlone() {
    RegisteredTestUser user = registerAndActivateCourier();
    String googleId = "google-" + UUID.randomUUID();
    fakeVerifier.nextResult =
        Optional.of(new GoogleIdentity(googleId, user.login() + "@uaiou.test", true));

    SessionRequest firstRequest =
        new SessionRequest("google", null, null, "token-primeiro-login", null, user.role());
    ResponseEntity<SessionResponse> firstResponse =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), firstRequest, SessionResponse.class);
    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(firstResponse.getBody().user().id()).isEqualTo(user.id());

    // Novo login: mesmo google_id, e-mail completamente diferente e não verificado — só resolve se
    // o
    // vínculo do primeiro login realmente persistiu (RF-03.6, "aproveita para vincular o
    // google_id").
    fakeVerifier.nextResult =
        Optional.of(
            new GoogleIdentity(googleId, "outro-completamente-diferente@uaiou.test", false));
    SessionRequest secondRequest =
        new SessionRequest("google", null, null, "token-segundo-login", null, user.role());
    ResponseEntity<SessionResponse> secondResponse =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), secondRequest, SessionResponse.class);

    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(secondResponse.getBody().user().id()).isEqualTo(user.id());
  }

  @TestConfiguration
  static class FakeGoogleConfig {
    @Bean
    @Primary
    FakeGoogleIdTokenVerifier fakeGoogleIdTokenVerifier() {
      return new FakeGoogleIdTokenVerifier();
    }
  }

  static class FakeGoogleIdTokenVerifier implements GoogleIdTokenVerifierPort {
    volatile Optional<GoogleIdentity> nextResult = Optional.empty();

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
      return nextResult;
    }
  }
}
