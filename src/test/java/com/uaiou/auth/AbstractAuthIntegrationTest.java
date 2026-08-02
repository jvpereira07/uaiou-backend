package com.uaiou.auth;

import com.uaiou.auth.dto.RegisterProfile;
import com.uaiou.auth.dto.RegisterRequest;
import com.uaiou.auth.dto.RegisterResponse;
import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.support.AbstractIntegrationTest;
import com.uaiou.support.UserModerationTestFixtures;
import com.uaiou.users.Role;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

/**
 * Fixtures compartilhadas pelos testes de integração de T-03: registrar e (quando o cenário exige)
 * ativar um entregador/estabelecimento de teste, e logar com ele. Ativação passa por SQL direto
 * ({@link UserModerationTestFixtures}) porque a rota de aprovação de cadastro é do T-07.
 */
abstract class AbstractAuthIntegrationTest extends AbstractIntegrationTest {

  protected static final String DEFAULT_PASSWORD = "senha-forte-o-suficiente";

  private static final AtomicLong SEQUENCE = new AtomicLong();

  @Autowired protected UserModerationTestFixtures moderation;

  protected record RegisteredTestUser(UUID id, String login, String password, Role role) {}

  protected RegisteredTestUser registerCourier() {
    String login = "entregador-" + uniqueSuffix();
    RegisterRequest request =
        new RegisterRequest(
            Role.COURIER,
            login,
            login + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Entregador de Teste",
            new RegisterProfile(uniqueDigits(11), "MOTORCYCLE", "ABC1D23", null, null));
    RegisterResponse response = register(request);
    return new RegisteredTestUser(response.id(), login, DEFAULT_PASSWORD, Role.COURIER);
  }

  protected RegisteredTestUser registerMerchant() {
    String login = "estabelecimento-" + uniqueSuffix();
    RegisterRequest request =
        new RegisterRequest(
            Role.MERCHANT,
            login,
            login + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Estabelecimento de Teste",
            new RegisterProfile(null, null, null, uniqueDigits(14), "Loja de Teste"));
    RegisterResponse response = register(request);
    return new RegisteredTestUser(response.id(), login, DEFAULT_PASSWORD, Role.MERCHANT);
  }

  protected RegisteredTestUser registerAndActivateCourier() {
    RegisteredTestUser user = registerCourier();
    moderation.activate(user.id());
    return user;
  }

  protected RegisteredTestUser registerAndActivateMerchant() {
    RegisteredTestUser user = registerMerchant();
    moderation.activate(user.id());
    return user;
  }

  protected RegisterResponse register(RegisterRequest request) {
    ResponseEntity<RegisterResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/registrations"), request, RegisterResponse.class);
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("Falha ao registrar usuário de teste: " + response);
    }
    return response.getBody();
  }

  protected SessionResponse login(RegisteredTestUser user) {
    return loginPassword(user.login(), user.password(), user.role());
  }

  protected SessionResponse loginPassword(String login, String password, Role role) {
    SessionRequest request = new SessionRequest("password", login, password, null, null, role);
    ResponseEntity<SessionResponse> response =
        restTemplate.postForEntity(baseUrl("/auth/sessions"), request, SessionResponse.class);
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("Falha ao autenticar usuário de teste: " + response);
    }
    return response.getBody();
  }

  protected static String uniqueSuffix() {
    return UUID.randomUUID().toString();
  }

  protected static String uniqueDigits(int length) {
    long value = (System.nanoTime() & Long.MAX_VALUE) + SEQUENCE.incrementAndGet();
    String digits = Long.toString(value);
    if (digits.length() < length) {
      digits = "0".repeat(length - digits.length()) + digits;
    }
    return digits.substring(digits.length() - length);
  }
}
