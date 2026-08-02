package com.uaiou.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.UploadTestFixtures;
import com.uaiou.users.dto.Address;
import com.uaiou.users.dto.MeResponse;
import com.uaiou.users.dto.PatchMeProfile;
import com.uaiou.users.dto.PatchMeRequest;
import com.uaiou.users.repository.DocumentoCadastroRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-04.3, RF-04.4 — critérios de aceite 3, 4 e 5 de T-04. */
class ProfilePatchIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private UploadTestFixtures uploads;
  @Autowired private DocumentoCadastroRepository documentoCadastroRepository;

  @Test
  void displayNameAndTelefoneApplyImmediately() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();

    PatchMeRequest request = new PatchMeRequest("Novo Nome de Exibição", "31999998888", null);
    ResponseEntity<MeResponse> response = patchMe(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().displayName()).isEqualTo("Novo Nome de Exibição");
    assertThat(response.getBody().telefone()).isEqualTo("31999998888");
    assertThat(response.getBody().pendingFields()).isEmpty();
  }

  @Test
  void cnpjEditDoesNotChangeTheValueButCreatesAPendingDocument() {
    RegisteredTestUser user = registerAndActivateMerchant();
    SessionResponse session = login(user);
    String originalCnpj = getMe(session.accessToken()).getBody().profile().cnpj();
    UUID uploadId = uploads.createReadyUpload(user.id(), "documento_cnpj");

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null,
                null,
                null,
                null,
                null,
                "99999999000199",
                uploadId,
                null,
                null,
                null,
                null,
                null,
                null));
    ResponseEntity<MeResponse> response = patchMe(session.accessToken(), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().pendingFields()).containsExactly("cnpj");
    // O valor no perfil segue o que estava no banco — a edição não aplicou direto (critério de
    // aceite 4).
    assertThat(response.getBody().profile().cnpj()).isEqualTo(originalCnpj);
    assertThat(documentoCadastroRepository.existsByUploadId(uploadId)).isTrue();
  }

  @Test
  void verifiedFieldWithoutUploadIdIsRejected() {
    RegisteredTestUser user = registerAndActivateMerchant();
    String accessToken = login(user).accessToken();

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null,
                null,
                null,
                null,
                null,
                "99999999000199",
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    ResponseEntity<ErrorResponse> response = patchMeExpectingError(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("MISSING_FIELD");
  }

  @Test
  void verifiedFieldWithAnUploadThatIsNotReadyIsRejected() {
    RegisteredTestUser user = registerAndActivateMerchant();
    String accessToken = login(user).accessToken();
    UUID uploadId = uploads.createAwaitingUpload(user.id(), "documento_cnpj");

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null,
                null,
                null,
                null,
                null,
                "99999999000199",
                uploadId,
                null,
                null,
                null,
                null,
                null,
                null));
    ResponseEntity<ErrorResponse> response = patchMeExpectingError(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("UPLOAD_NOT_READY");
  }

  @Test
  void verifiedFieldWithAnUploadOfTheWrongPurposeIsRejected() {
    RegisteredTestUser user = registerAndActivateMerchant();
    String accessToken = login(user).accessToken();
    UUID uploadId = uploads.createReadyUpload(user.id(), "documento_identidade");

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null,
                null,
                null,
                null,
                null,
                "99999999000199",
                uploadId,
                null,
                null,
                null,
                null,
                null,
                null));
    ResponseEntity<ErrorResponse> response = patchMeExpectingError(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("UPLOAD_PURPOSE_MISMATCH");
  }

  @Test
  void reusingAnUploadAlreadyConsumedByAnotherDocumentIsRejected() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = uploads.createReadyUpload(user.id(), "documento_identidade");

    PatchMeRequest firstRequest =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                "11122233344",
                uploadId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    ResponseEntity<MeResponse> firstResponse = patchMe(accessToken, firstRequest);
    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    PatchMeRequest secondRequest =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                "55566677788",
                uploadId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    ResponseEntity<ErrorResponse> secondResponse =
        patchMeExpectingError(accessToken, secondRequest);

    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(secondResponse.getBody().error().code()).isEqualTo("UPLOAD_ALREADY_USED");
  }

  @Test
  void vehicleTypeAndPlateShareOneUploadAndBothBecomePending() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = uploads.createReadyUpload(user.id(), "documento_veiculo");

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null, null, "CAR", "XYZ9K88", uploadId, null, null, null, null, null, null, null,
                null));
    ResponseEntity<MeResponse> response = patchMe(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().pendingFields())
        .containsExactlyInAnyOrder("vehicleType", "vehiclePlate");
  }

  @Test
  void merchantCannotEditCourierFields() {
    RegisteredTestUser user = registerAndActivateMerchant();
    String accessToken = login(user).accessToken();

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                "11122233344",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
    ResponseEntity<ErrorResponse> response = patchMeExpectingError(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("PROFILE_FIELD_NOT_APPLICABLE");
  }

  @Test
  void courierCannotEditMerchantFields() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "algum-object-key",
                null,
                null,
                null,
                null,
                null));
    ResponseEntity<ErrorResponse> response = patchMeExpectingError(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("PROFILE_FIELD_NOT_APPLICABLE");
  }

  @Test
  void merchantLogoAndAddressApplyImmediately() {
    RegisteredTestUser user = registerAndActivateMerchant();
    String accessToken = login(user).accessToken();

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "logo-key-123",
                "Centro",
                "Rua das Flores",
                "100",
                "Belo Horizonte",
                "30130000"));
    ResponseEntity<MeResponse> response = patchMe(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().pendingFields()).isEmpty();
    assertThat(response.getBody().profile().logoObjectKey()).isEqualTo("logo-key-123");
    Address address = response.getBody().profile().address();
    assertThat(address.bairro()).isEqualTo("Centro");
    assertThat(address.cidade()).isEqualTo("Belo Horizonte");
  }

  @Test
  void editingOnlyOneAddressFieldPreservesTheOthers() {
    RegisteredTestUser user = registerAndActivateMerchant();
    String accessToken = login(user).accessToken();
    patchMe(
        accessToken,
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Centro",
                "Rua das Flores",
                "100",
                "Belo Horizonte",
                "30130000")));

    // Só o CEP muda desta vez — bairro/rua/número/cidade não foram enviados e precisam permanecer.
    ResponseEntity<MeResponse> response =
        patchMe(
            accessToken,
            new PatchMeRequest(
                null,
                null,
                new PatchMeProfile(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "30140000")));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    Address address = response.getBody().profile().address();
    assertThat(address.cep()).isEqualTo("30140000");
    assertThat(address.bairro()).isEqualTo("Centro");
    assertThat(address.rua()).isEqualTo("Rua das Flores");
    assertThat(address.numero()).isEqualTo("100");
    assertThat(address.cidade()).isEqualTo("Belo Horizonte");
  }

  @Test
  void vehicleUploadIdWithoutAVehicleFieldIsRejected() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = uploads.createReadyUpload(user.id(), "documento_veiculo");

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                null, null, null, null, uploadId, null, null, null, null, null, null, null, null));
    ResponseEntity<ErrorResponse> response = patchMeExpectingError(accessToken, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("UNEXPECTED_FIELD");
  }

  private ResponseEntity<MeResponse> getMe(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return restTemplate.exchange(
        baseUrl("/me"), HttpMethod.GET, new HttpEntity<>(headers), MeResponse.class);
  }

  private ResponseEntity<MeResponse> patchMe(String accessToken, PatchMeRequest request) {
    return restTemplate.exchange(
        baseUrl("/me"), HttpMethod.PATCH, entityWithAuth(accessToken, request), MeResponse.class);
  }

  private ResponseEntity<ErrorResponse> patchMeExpectingError(
      String accessToken, PatchMeRequest request) {
    return restTemplate.exchange(
        baseUrl("/me"),
        HttpMethod.PATCH,
        entityWithAuth(accessToken, request),
        ErrorResponse.class);
  }

  private HttpEntity<PatchMeRequest> entityWithAuth(String accessToken, PatchMeRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return new HttpEntity<>(request, headers);
  }
}
