package com.uaiou.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.uaiou.auth.dto.PasswordResetConfirmation;
import com.uaiou.auth.dto.PasswordResetRequest;
import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Critério de aceite 11 de T-03: nenhum log carrega senha, idToken ou refresh token em claro. */
class AuthLoggingIntegrationTest extends AbstractAuthIntegrationTest {

  private ListAppender<ILoggingEvent> appender;
  private Logger rootLogger;

  @BeforeEach
  void attachAppender() {
    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    appender = new ListAppender<>();
    appender.start();
    rootLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    rootLogger.detachAppender(appender);
  }

  @Test
  void aFullAuthFlowNeverLogsThePasswordOrAnyToken() {
    RegisteredTestUser user = registerAndActivateCourier();

    // Caminho de erro (senha errada) — é o que mais tenderia a "explicar" o motivo logando o valor
    // recebido.
    restTemplate.postForEntity(
        baseUrl("/auth/sessions"),
        new SessionRequest(
            "password", user.login(), "senha-errada-de-proposito-1234", null, null, user.role()),
        Object.class);

    SessionResponse session = login(user);

    SessionRequest refreshRequest =
        new SessionRequest("refresh", null, null, null, session.refreshToken(), null);
    ResponseEntity<SessionResponse> refreshed =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"), refreshRequest, SessionResponse.class);

    restTemplate.postForEntity(
        baseUrl("/auth/password-resets"),
        new PasswordResetRequest(user.login() + "@uaiou.test"),
        Void.class);
    restTemplate.exchange(
        baseUrl("/auth/password-resets/pr_token-forjado-que-nao-existe"),
        HttpMethod.PUT,
        new HttpEntity<>(new PasswordResetConfirmation("outra-senha-bem-secreta-tambem")),
        Object.class);

    List<String> allLoggedMessages =
        appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    String everything = String.join("\n", allLoggedMessages);

    assertThat(everything).doesNotContain(user.password());
    assertThat(everything).doesNotContain("senha-errada-de-proposito-1234");
    assertThat(everything).doesNotContain("outra-senha-bem-secreta-tambem");
    assertThat(everything).doesNotContain(session.accessToken());
    assertThat(everything).doesNotContain(session.refreshToken());
    assertThat(everything).doesNotContain(refreshed.getBody().refreshToken());
    // O token de redefinição de senha NÃO entra nesta lista de propósito: ele viaja na URL por
    // desenho da
    // rota (PUT /auth/password-resets/{token}), e RequestLoggingFilter loga método+caminho
    // (RF-01.9) — isso
    // é log de acesso padrão, não o vazamento de senha/idToken/refreshToken que o critério 11
    // proíbe.
  }
}
