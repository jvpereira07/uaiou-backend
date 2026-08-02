package com.uaiou.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.RegisterProfile;
import com.uaiou.auth.dto.RegisterRequest;
import com.uaiou.auth.dto.RegisterResponse;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.uploads.Purpose;
import com.uaiou.users.dto.DocumentsResponse;
import com.uaiou.users.dto.SubmitDocumentRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-06.1, RF-06.3 — critério de aceite 1 de T-06. */
class DocumentsListIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void freshlyRegisteredCourierListsAllThreeTypesAsMissing() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();

    ResponseEntity<DocumentsResponse> response = getDocuments(accessToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().documents()).isEmpty();
    assertThat(response.getBody().missingTypes())
        .containsExactlyInAnyOrder(
            Purpose.IDENTITY_DOCUMENT, Purpose.VEHICLE_DOCUMENT, Purpose.DRIVER_LICENSE);
  }

  @Test
  void freshlyRegisteredMerchantListsOnlyCnpjAsMissing() {
    RegisteredTestUser user = registerAndActivateMerchant();
    String accessToken = login(user).accessToken();

    ResponseEntity<DocumentsResponse> response = getDocuments(accessToken);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().missingTypes()).containsExactly(Purpose.CNPJ_DOCUMENT);
  }

  @Test
  void bicycleCourierDoesNotNeedADriverLicense() {
    String login = "entregador-bike-" + uniqueSuffix();
    RegisterRequest request =
        new RegisterRequest(
            Role.COURIER,
            login,
            login + "@uaiou.test",
            DEFAULT_PASSWORD,
            "Entregador de Bike",
            new RegisterProfile(uniqueDigits(11), "BICYCLE", "BIC1D23", null, null));
    RegisterResponse registered = register(request);
    moderation.activate(registered.id());
    SessionResponse session = loginPassword(login, DEFAULT_PASSWORD, Role.COURIER);

    ResponseEntity<DocumentsResponse> response = getDocuments(session.accessToken());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().missingTypes())
        .containsExactlyInAnyOrder(Purpose.IDENTITY_DOCUMENT, Purpose.VEHICLE_DOCUMENT);
  }

  @Test
  void aSubmittedTypeStopsAppearingAsMissingAndShowsUpInDocuments() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = createAndConfirmUpload(accessToken, Purpose.IDENTITY_DOCUMENT);
    restTemplate.exchange(
        baseUrl("/me/documents"),
        HttpMethod.POST,
        authed(accessToken, new SubmitDocumentRequest(Purpose.IDENTITY_DOCUMENT, uploadId)),
        Object.class);

    ResponseEntity<DocumentsResponse> response = getDocuments(accessToken);

    assertThat(response.getBody().missingTypes())
        .containsExactlyInAnyOrder(Purpose.VEHICLE_DOCUMENT, Purpose.DRIVER_LICENSE);
    assertThat(response.getBody().documents()).hasSize(1);
    assertThat(response.getBody().documents().get(0).type()).isEqualTo(Purpose.IDENTITY_DOCUMENT);
    assertThat(response.getBody().documents().get(0).status())
        .isEqualTo(DocumentApprovalStatus.PENDING);
  }

  private ResponseEntity<DocumentsResponse> getDocuments(String accessToken) {
    return restTemplate.exchange(
        baseUrl("/me/documents"),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(accessToken)),
        DocumentsResponse.class);
  }
}
