package com.uaiou.support;

import com.uaiou.auth.dto.RegisterProfile;
import com.uaiou.auth.dto.RegisterRequest;
import com.uaiou.auth.dto.RegisterResponse;
import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.uploads.Purpose;
import com.uaiou.uploads.dto.CreateUploadRequest;
import com.uaiou.uploads.dto.CreateUploadResponse;
import com.uaiou.users.Role;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Fixtures compartilhadas por testes de integração de qualquer módulo que precise de um entregador/
 * estabelecimento de teste, registrado e (quando o cenário exige) ativado, com sessão válida.
 * Nasceu em T-03 (autenticação); movida para {@code support} em T-04 porque perfil (`/me`) também
 * precisa exatamente disso. Ativação passa por SQL direto ({@link UserModerationTestFixtures})
 * porque a rota de aprovação de cadastro é do T-07.
 */
public abstract class AbstractAuthIntegrationTest extends AbstractIntegrationTest {

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

  protected HttpHeaders authHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }

  protected <T> HttpEntity<T> authed(String accessToken, T body) {
    return new HttpEntity<>(body, authHeaders(accessToken));
  }

  protected ResponseEntity<CreateUploadResponse> createUpload(
      String accessToken, Purpose purpose, String contentType, long sizeBytes) {
    CreateUploadRequest request = new CreateUploadRequest(purpose, contentType, sizeBytes);
    return restTemplate.postForEntity(
        baseUrl("/uploads"), authed(accessToken, request), CreateUploadResponse.class);
  }

  protected <T> ResponseEntity<T> confirmUpload(
      String accessToken, UUID uploadId, Class<T> responseType) {
    return restTemplate.exchange(
        baseUrl("/uploads/" + uploadId),
        HttpMethod.PUT,
        new HttpEntity<>(null, authHeaders(accessToken)),
        responseType);
  }

  /**
   * {@code URI.create}, não {@code exchange(String, ...)}: a URL pré-assinada já vem com a
   * assinatura AWS SigV4 codificada na query string, e o {@code UriComponentsBuilder} que o
   * RestTemplate usa por trás de uma sobrecarga em String re-codifica a URL — dobrando o
   * percent-encoding e invalidando a assinatura. Passar um {@link URI} já pronto evita essa
   * re-codificação.
   */
  protected ResponseEntity<Void> putBytes(String presignedUrl, byte[] bytes, String contentType) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(contentType));
    return restTemplate.exchange(
        URI.create(presignedUrl), HttpMethod.PUT, new HttpEntity<>(bytes, headers), Void.class);
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
