package com.uaiou.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.RegisterProfile;
import com.uaiou.auth.dto.RegisterRequest;
import com.uaiou.auth.dto.RegisterResponse;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import com.uaiou.users.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-03.1, RF-03.2 — critérios de aceite 1 e 2 de T-03. */
class AuthRegistrationIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private UsuarioRepository usuarioRepository;

  @Test
  void courierRegistrationCreatesPendingUserWithCourierRequiredDocuments() {
    String login = "entregador-" + uniqueSuffix();
    RegisterRequest request =
        new RegisterRequest(
            Role.COURIER,
            login,
            login + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Entregador de Teste",
            new RegisterProfile(uniqueDigits(11), "MOTORCYCLE", "ABC1D23", null, null));

    ResponseEntity<RegisterResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/registrations"), request, RegisterResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    RegisterResponse body = response.getBody();
    assertThat(body.role()).isEqualTo(Role.COURIER);
    assertThat(body.status()).isEqualTo(UserStatus.PENDING);
    assertThat(body.requiredDocuments())
        .containsExactly("IDENTITY_DOCUMENT", "DRIVER_LICENSE", "VEHICLE_DOCUMENT");
    assertThat(body.links()).containsKeys("self", "documents");
  }

  @Test
  void merchantRegistrationCreatesPendingUserWithMerchantRequiredDocuments() {
    String login = "estabelecimento-" + uniqueSuffix();
    RegisterRequest request =
        new RegisterRequest(
            Role.MERCHANT,
            login,
            login + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Estabelecimento de Teste",
            new RegisterProfile(null, null, null, uniqueDigits(14), "Loja de Teste"));

    ResponseEntity<RegisterResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/registrations"), request, RegisterResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    RegisterResponse body = response.getBody();
    assertThat(body.role()).isEqualTo(Role.MERCHANT);
    assertThat(body.status()).isEqualTo(UserStatus.PENDING);
    assertThat(body.requiredDocuments()).containsExactly("CNPJ_DOCUMENT");
  }

  @Test
  void adminSelfRegistrationIsRejected() {
    String login = "admin-" + uniqueSuffix();
    RegisterRequest request =
        new RegisterRequest(
            Role.ADMIN,
            login,
            login + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Admin de Teste",
            new RegisterProfile(null, null, null, null, null));

    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/registrations"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("ADMIN_SELF_REGISTRATION_NOT_ALLOWED");
  }

  @Test
  void courierWithoutCpfIsRejected() {
    String login = "entregador-" + uniqueSuffix();
    RegisterRequest request =
        new RegisterRequest(
            Role.COURIER,
            login,
            login + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Entregador de Teste",
            new RegisterProfile(null, "MOTORCYCLE", "ABC1D23", null, null));

    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/registrations"), request, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("MISSING_PROFILE_FIELD");
  }

  @Test
  void duplicateLoginIsRejectedWithFieldInDetails() {
    RegisteredTestUser first = registerCourier();
    RegisterRequest duplicate =
        new RegisterRequest(
            Role.COURIER,
            first.login(),
            "outro-email-" + uniqueSuffix() + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Outro Entregador",
            new RegisterProfile(uniqueDigits(11), "MOTORCYCLE", "XYZ9W88", null, null));

    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/registrations"), duplicate, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("LOGIN_ALREADY_TAKEN");
    assertThat(response.getBody().error().details()).containsEntry("field", "login");
  }

  @Test
  void duplicateEmailIsRejected() {
    RegisteredTestUser first = registerCourier();
    RegisterRequest duplicate =
        new RegisterRequest(
            Role.COURIER,
            "outro-login-" + uniqueSuffix(),
            first.login() + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Outro Entregador",
            new RegisterProfile(uniqueDigits(11), "MOTORCYCLE", "XYZ9W88", null, null));

    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/registrations"), duplicate, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("EMAIL_ALREADY_TAKEN");
  }

  @Test
  void duplicateCnpjIsRejected() {
    String sharedCnpj = uniqueDigits(14);
    String firstLogin = "estabelecimento-" + uniqueSuffix();
    RegisterRequest firstRequest =
        new RegisterRequest(
            Role.MERCHANT,
            firstLogin,
            firstLogin + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Primeiro Estabelecimento",
            new RegisterProfile(null, null, null, sharedCnpj, "Loja Um"));
    register(firstRequest);

    String secondLogin = "estabelecimento-" + uniqueSuffix();
    RegisterRequest secondRequest =
        new RegisterRequest(
            Role.MERCHANT,
            secondLogin,
            secondLogin + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Segundo Estabelecimento",
            new RegisterProfile(null, null, null, sharedCnpj, "Loja Dois"));

    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(
            baseUrl("/auth/registrations"), secondRequest, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("CNPJ_ALREADY_TAKEN");
  }

  @Test
  void duplicateCpfIsRejectedAndTheOrphanedUsuarioIsRolledBack() {
    String sharedCpf = uniqueDigits(11);
    String firstLogin = "entregador-" + uniqueSuffix();
    RegisterRequest firstRequest =
        new RegisterRequest(
            Role.COURIER,
            firstLogin,
            firstLogin + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Primeiro Entregador",
            new RegisterProfile(sharedCpf, "MOTORCYCLE", "ABC1D23", null, null));
    register(firstRequest);

    String secondLogin = "entregador-" + uniqueSuffix();
    RegisterRequest secondRequest =
        new RegisterRequest(
            Role.COURIER,
            secondLogin,
            secondLogin + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Segundo Entregador",
            new RegisterProfile(sharedCpf, "MOTORCYCLE", "XYZ9W88", null, null));

    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(
            baseUrl("/auth/registrations"), secondRequest, ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("CPF_ALREADY_TAKEN");
    // Critério de aceite 1: a falha no INSERT da linha-filha (entregador, por cpf duplicado) não
    // deixa o
    // "usuario" da segunda tentativa órfão no banco — a transação inteira precisa ter voltado
    // atrás.
    assertThat(usuarioRepository.findByLogin(secondLogin)).isEmpty();
  }
}
