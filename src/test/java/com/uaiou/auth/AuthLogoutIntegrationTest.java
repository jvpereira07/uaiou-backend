package com.uaiou.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.LogoutRequest;
import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.shared.error.ErrorResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * RF-03.9, RF-03.10 — critérios de aceite 10 e (implicitamente) 4 de T-03, agora sob rota de
 * escrita real.
 */
class AuthLogoutIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void logoutWithoutATokenIsRejected() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/auth/sessions/current"), HttpMethod.DELETE, null, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error().code()).isEqualTo("MISSING_TOKEN");
  }

  @Test
  void logoutWithAGarbageTokenIsRejected() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth("isto-nao-eh-um-jwt-valido");

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/auth/sessions/current"),
            HttpMethod.DELETE,
            new HttpEntity<>(null, headers),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void logoutRevokesTheGivenRefreshTokenAndItStopsWorking() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse session = login(user);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(session.accessToken());
    ResponseEntity<Void> logoutResponse =
        restTemplate.exchange(
            baseUrl("/auth/sessions/current"),
            HttpMethod.DELETE,
            new HttpEntity<>(new LogoutRequest(session.refreshToken()), headers),
            Void.class);
    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    SessionRequest refreshRequest =
        new SessionRequest("refresh", null, null, null, session.refreshToken(), null);
    ResponseEntity<ErrorResponse> refreshAfterLogout =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), refreshRequest, ErrorResponse.class);
    assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void logoutWithoutABodyIsANoOpButStillRequiresAuthentication() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse session = login(user);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(session.accessToken());
    ResponseEntity<Void> logoutResponse =
        restTemplate.exchange(
            baseUrl("/auth/sessions/current"),
            HttpMethod.DELETE,
            new HttpEntity<>(null, headers),
            Void.class);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Sem refreshToken no corpo, nada foi revogado — a sessão original segue utilizável.
    SessionRequest refreshRequest =
        new SessionRequest("refresh", null, null, null, session.refreshToken(), null);
    ResponseEntity<SessionResponse> refreshResponse =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"), refreshRequest, SessionResponse.class);
    assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  @Test
  void aBannedUserIsBlockedOnTheVeryNextWriteRequestEvenWithAStillValidAccessToken() {
    // Critério de aceite 10: o middleware reconsulta o status no banco em rota de escrita — não
    // confia no
    // "status" cacheado no token, senão um usuário banido durante a vida do access token
    // continuaria
    // operando até ele expirar (RF-03.9).
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse session = login(user);

    moderation.ban(user.id(), "Comportamento fraudulento identificado após o login.");

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(session.accessToken());
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/auth/sessions/current"),
            HttpMethod.DELETE,
            new HttpEntity<>(null, headers),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("ACCOUNT_BANNED");
  }

  @Test
  void aSuspendedUserIsBlockedOnTheNextWriteRequestWithTheDeadlineInTheError() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse session = login(user);
    Instant until = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    moderation.suspend(user.id(), "Reincidência identificada após o login.", until);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(session.accessToken());
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/auth/sessions/current"),
            HttpMethod.DELETE,
            new HttpEntity<>(null, headers),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("ACCOUNT_SUSPENDED");
    assertThat(response.getBody().error().details()).containsEntry("until", until.toString());
  }
}
