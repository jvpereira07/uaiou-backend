package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uaiou.admin.dto.CreateSanctionRequest;
import com.uaiou.admin.dto.SanctionSummary;
import com.uaiou.admin.service.SanctionService;
import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.SanctionType;
import com.uaiou.users.UserStatus;
import com.uaiou.users.dto.PatchMeRequest;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * RF-07.5 a RF-07.7 — critérios de aceite 3, 4, 5, 7 de T-07 (6 depende de T-13/T-15, fora daqui).
 */
class SanctionIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private SanctionService sanctionService;
  @Autowired private UsuarioRepository usuarioRepository;

  @Test
  void suspendingRevokesRefreshTokensAndBlocksTheNextWriteRequest() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();
    SessionResponse session = login(courier);

    ResponseEntity<SanctionSummary> response =
        applySanction(
            admin.accessToken(),
            courier.id(),
            new CreateSanctionRequest(
                SanctionType.SUSPENSION,
                "3 disputas perdidas.",
                Instant.now().plus(7, ChronoUnit.DAYS)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().active()).isTrue();
    assertThat(usuarioRepository.findById(courier.id()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.SUSPENDED);

    ResponseEntity<ErrorResponse> refreshResponse =
        restTemplate.postForEntity(
            baseUrl("/auth/sessions"),
            new SessionRequest("refresh", null, null, null, session.refreshToken(), null),
            ErrorResponse.class);
    assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ResponseEntity<ErrorResponse> patchResponse =
        restTemplate.exchange(
            baseUrl("/me"),
            HttpMethod.PATCH,
            authed(session.accessToken(), new PatchMeRequest("Novo nome", null, null)),
            ErrorResponse.class);
    assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(patchResponse.getBody().error().code()).isEqualTo("ACCOUNT_SUSPENDED");
  }

  @Test
  void suspensionWithoutExpiresAtIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();

    ResponseEntity<ErrorResponse> response =
        applySanctionExpectingError(
            admin.accessToken(),
            courier.id(),
            new CreateSanctionRequest(SanctionType.SUSPENSION, "Motivo qualquer.", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("MISSING_FIELD");
  }

  @Test
  void banWithExpiresAtIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();

    ResponseEntity<ErrorResponse> response =
        applySanctionExpectingError(
            admin.accessToken(),
            courier.id(),
            new CreateSanctionRequest(
                SanctionType.BAN, "Fraude.", Instant.now().plus(1, ChronoUnit.DAYS)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("UNEXPECTED_FIELD");
  }

  @Test
  void banningActivatesPermanentlyAndBlocksLogin() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();

    ResponseEntity<SanctionSummary> response =
        applySanction(
            admin.accessToken(),
            courier.id(),
            new CreateSanctionRequest(SanctionType.BAN, "Fraude grave.", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().end()).isNull();
    assertThat(usuarioRepository.findById(courier.id()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.BANNED);
  }

  @Test
  void applyingASecondSanctionWhileOneIsActiveIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();
    applySanction(
        admin.accessToken(),
        courier.id(),
        new CreateSanctionRequest(
            SanctionType.SUSPENSION, "Primeira.", Instant.now().plus(1, ChronoUnit.DAYS)));

    ResponseEntity<ErrorResponse> response =
        applySanctionExpectingError(
            admin.accessToken(),
            courier.id(),
            new CreateSanctionRequest(
                SanctionType.SUSPENSION, "Segunda.", Instant.now().plus(2, ChronoUnit.DAYS)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("USER_ALREADY_SANCTIONED");
  }

  @Test
  void reactivatingEarlyReturnsTheAccountToActive() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();
    SanctionSummary sanction =
        applySanction(
                admin.accessToken(),
                courier.id(),
                new CreateSanctionRequest(
                    SanctionType.SUSPENSION, "Motivo.", Instant.now().plus(7, ChronoUnit.DAYS)))
            .getBody();

    ResponseEntity<Void> response = reactivate(admin.accessToken(), sanction.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(usuarioRepository.findById(courier.id()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void reactivatingAnAlreadyInactiveSanctionIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();
    SanctionSummary sanction =
        applySanction(
                admin.accessToken(),
                courier.id(),
                new CreateSanctionRequest(
                    SanctionType.SUSPENSION, "Motivo.", Instant.now().plus(7, ChronoUnit.DAYS)))
            .getBody();
    reactivate(admin.accessToken(), sanction.id());

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/admin/sanctions/" + sanction.id()),
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders(admin.accessToken())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("SANCTION_ALREADY_INACTIVE");
  }

  /**
   * Critério de aceite 4: falha na transação da sanção não deixa auditoria órfã nem status
   * alterado. Força a falha com uma violação real de FK (admin_id inexistente na tabela {@code
   * admin}) em vez de mock — {@link SanctionService#apply} não valida a existência do admin porque
   * confia no chamador (rota já autentica um admin de verdade); aqui o teste é o próprio chamador
   * "malicioso" que quebra essa premissa de propósito, só para provar que o rollback é real.
   */
  @Test
  void aFailedSanctionTransactionRollsBackEverything() {
    RegisteredTestUser courier = registerAndActivateCourier();
    UUID bogusAdminId = UUID.randomUUID();
    CreateSanctionRequest request =
        new CreateSanctionRequest(
            SanctionType.SUSPENSION,
            "Nunca deveria persistir.",
            Instant.now().plus(1, ChronoUnit.DAYS));

    assertThatThrownBy(() -> sanctionService.apply(bogusAdminId, courier.id(), request))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(usuarioRepository.findById(courier.id()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
  }

  private ResponseEntity<SanctionSummary> applySanction(
      String adminToken, UUID userId, CreateSanctionRequest request) {
    return restTemplate.exchange(
        baseUrl("/admin/users/" + userId + "/sanctions"),
        HttpMethod.POST,
        authed(adminToken, request),
        SanctionSummary.class);
  }

  private ResponseEntity<ErrorResponse> applySanctionExpectingError(
      String adminToken, UUID userId, CreateSanctionRequest request) {
    return restTemplate.exchange(
        baseUrl("/admin/users/" + userId + "/sanctions"),
        HttpMethod.POST,
        authed(adminToken, request),
        ErrorResponse.class);
  }

  private ResponseEntity<Void> reactivate(String adminToken, UUID sanctionId) {
    return restTemplate.exchange(
        baseUrl("/admin/sanctions/" + sanctionId),
        HttpMethod.DELETE,
        new HttpEntity<>(authHeaders(adminToken)),
        Void.class);
  }
}
