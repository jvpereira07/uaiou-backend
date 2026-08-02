package com.uaiou.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.PasswordResetConfirmation;
import com.uaiou.auth.dto.PasswordResetRequest;
import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.auth.service.EmailSender;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

/** RF-03.11 — critérios de aceite 8 e 9 de T-03. */
@Import(AuthPasswordResetIntegrationTest.FakeEmailConfig.class)
class AuthPasswordResetIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private CapturingEmailSender emailSender;

  @BeforeEach
  void resetCapturedEmails() {
    emailSender.sentTo.clear();
    emailSender.lastLink = null;
  }

  @Test
  void requestAlwaysRespondsAcceptedRegardlessOfWhetherTheEmailExists() {
    RegisteredTestUser user = registerAndActivateCourier();
    String existingEmail = user.login() + "@uaiou.test";
    String nonexistentEmail = "ninguem-" + uniqueSuffix() + "@uaiou.test";

    ResponseEntity<Void> forExisting =
        restTemplate.postForEntity(
            baseUrl("/auth/password-resets"), new PasswordResetRequest(existingEmail), Void.class);
    ResponseEntity<Void> forNonexistent =
        restTemplate.postForEntity(
            baseUrl("/auth/password-resets"),
            new PasswordResetRequest(nonexistentEmail),
            Void.class);

    assertThat(forExisting.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(forNonexistent.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    // Mesma resposta nos dois casos (critério 9) — só o e-mail existente de fato dispara o envio.
    assertThat(emailSender.sentTo).containsExactly(existingEmail);
  }

  @Test
  void confirmingWithAValidTokenChangesThePasswordAndRevokesAllRefreshTokens() {
    RegisteredTestUser user = registerAndActivateCourier();
    SessionResponse sessionBeforeReset = login(user);
    String email = user.login() + "@uaiou.test";

    restTemplate.postForEntity(
        baseUrl("/auth/password-resets"), new PasswordResetRequest(email), Void.class);
    String token = tokenFromLastLink();

    String newPassword = "nova-senha-bem-mais-forte-ainda";
    ResponseEntity<Void> confirmResponse =
        restTemplate.exchange(
            baseUrl("/auth/password-resets/" + token),
            HttpMethod.PUT,
            new HttpEntity<>(new PasswordResetConfirmation(newPassword)),
            Void.class);
    assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<ErrorResponse> oldPasswordAttempt =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"),
            new SessionRequest("password", user.login(), user.password(), null, null, user.role()),
            ErrorResponse.class);
    assertThat(oldPasswordAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ResponseEntity<SessionResponse> newPasswordAttempt =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"),
            new SessionRequest("password", user.login(), newPassword, null, null, user.role()),
            SessionResponse.class);
    assertThat(newPasswordAttempt.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Critério de aceite 8: refresh emitido antes da troca de senha deixou de funcionar.
    ResponseEntity<ErrorResponse> oldRefreshAttempt =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"),
            new SessionRequest(
                "refresh", null, null, null, sessionBeforeReset.refreshToken(), null),
            ErrorResponse.class);
    assertThat(oldRefreshAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void confirmingTwiceWithTheSameTokenFailsTheSecondTime() {
    RegisteredTestUser user = registerAndActivateCourier();
    restTemplate.postForEntity(
        baseUrl("/auth/password-resets"),
        new PasswordResetRequest(user.login() + "@uaiou.test"),
        Void.class);
    String token = tokenFromLastLink();

    restTemplate.exchange(
        baseUrl("/auth/password-resets/" + token),
        HttpMethod.PUT,
        new HttpEntity<>(new PasswordResetConfirmation("primeira-troca-valida-123")),
        Void.class);

    ResponseEntity<ErrorResponse> secondAttempt =
        restTemplate.exchange(
            baseUrl("/auth/password-resets/" + token),
            HttpMethod.PUT,
            new HttpEntity<>(new PasswordResetConfirmation("segunda-troca-nao-deveria-valer")),
            ErrorResponse.class);

    assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(secondAttempt.getBody().error().code()).isEqualTo("INVALID_RESET_TOKEN");
  }

  @Test
  void confirmingWithAnUnknownTokenIsRejected() {
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/auth/password-resets/pr_token-que-nunca-existiu"),
            HttpMethod.PUT,
            new HttpEntity<>(new PasswordResetConfirmation("qualquer-senha-123")),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().error().code()).isEqualTo("INVALID_RESET_TOKEN");
  }

  private String tokenFromLastLink() {
    return UriComponentsBuilder.fromUriString(emailSender.lastLink)
        .build()
        .getQueryParams()
        .getFirst("token");
  }

  @TestConfiguration
  static class FakeEmailConfig {
    @Bean
    @Primary
    CapturingEmailSender capturingEmailSender() {
      return new CapturingEmailSender();
    }
  }

  static class CapturingEmailSender implements EmailSender {
    final List<String> sentTo = new CopyOnWriteArrayList<>();
    volatile String lastLink;

    @Override
    public void sendPasswordReset(String toEmail, String resetLink) {
      sentTo.add(toEmail);
      lastLink = resetLink;
    }
  }
}
