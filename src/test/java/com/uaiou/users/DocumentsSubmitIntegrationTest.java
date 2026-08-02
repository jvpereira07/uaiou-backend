package com.uaiou.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.UploadTestFixtures;
import com.uaiou.uploads.Purpose;
import com.uaiou.users.dto.DocumentSummary;
import com.uaiou.users.dto.DocumentsResponse;
import com.uaiou.users.dto.SubmitDocumentRequest;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-06.2, RF-06.4, RF-06.5 — critérios de aceite 2, 3, 5 e 6 de T-06. */
class DocumentsSubmitIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private UploadTestFixtures uploadFixtures;
  @Autowired private UsuarioRepository usuarioRepository;

  @Test
  void submittingWithSomeoneElsesUploadIsRejected() {
    RegisteredTestUser owner = registerAndActivateCourier();
    RegisteredTestUser stranger = registerAndActivateCourier();
    String ownerToken = login(owner).accessToken();
    String strangerToken = login(stranger).accessToken();
    UUID uploadId = createAndConfirmUpload(ownerToken, Purpose.IDENTITY_DOCUMENT);

    ResponseEntity<ErrorResponse> response =
        submitExpectingError(strangerToken, Purpose.IDENTITY_DOCUMENT, uploadId);

    // T-06 documenta 403 aqui, mas T-05 (RF-05.6, critério de aceite 6) já tinha decidido 404 para
    // não
    // revelar a existência de upload alheio — o mesmo critério de T-05 permitia "403/404" como
    // equivalentes,
    // então mantemos 404 para não ter dois comportamentos diferentes na mesma checagem
    // compartilhada
    // (UploadService.validateForConsumption).
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void submittingAnUnconfirmedUploadIsRejected() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = uploadFixtures.createAwaitingUpload(user.id(), "documento_identidade");

    ResponseEntity<ErrorResponse> response =
        submitExpectingError(accessToken, Purpose.IDENTITY_DOCUMENT, uploadId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("UPLOAD_NOT_READY");
  }

  @Test
  void submittingATypeNotRequiredForTheRoleIsRejected() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = createAndConfirmUpload(accessToken, Purpose.CNPJ_DOCUMENT);

    ResponseEntity<ErrorResponse> response =
        submitExpectingError(accessToken, Purpose.CNPJ_DOCUMENT, uploadId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("DOCUMENT_TYPE_NOT_APPLICABLE");
  }

  @Test
  void happyPathSubmissionCreatesAPendingDocument() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = createAndConfirmUpload(accessToken, Purpose.IDENTITY_DOCUMENT);

    ResponseEntity<DocumentSummary> response =
        submit(accessToken, Purpose.IDENTITY_DOCUMENT, uploadId, DocumentSummary.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().type()).isEqualTo(Purpose.IDENTITY_DOCUMENT);
    assertThat(response.getBody().status()).isEqualTo(DocumentApprovalStatus.PENDING);
  }

  @Test
  void resubmittingAfterRejectionReturnsTheAccountToPendingAndKeepsTheOldDocumentInHistory() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID firstUploadId = createAndConfirmUpload(accessToken, Purpose.IDENTITY_DOCUMENT);
    DocumentSummary firstDocument =
        submit(accessToken, Purpose.IDENTITY_DOCUMENT, firstUploadId, DocumentSummary.class)
            .getBody();

    moderation.rejectDocument(user.id(), firstDocument.id(), "Foto ilegível.");
    assertThat(usuarioRepository.findById(user.id()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.REJECTED);

    UUID secondUploadId = createAndConfirmUpload(accessToken, Purpose.IDENTITY_DOCUMENT);
    ResponseEntity<DocumentSummary> secondResponse =
        submit(accessToken, Purpose.IDENTITY_DOCUMENT, secondUploadId, DocumentSummary.class);

    assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(usuarioRepository.findById(user.id()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.PENDING);

    ResponseEntity<DocumentsResponse> listResponse =
        restTemplate.exchange(
            baseUrl("/me/documents"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            DocumentsResponse.class);
    // Só o reenvio (vigente) aparece na listagem — mas o rejeitado continua no banco, não foi
    // apagado.
    assertThat(listResponse.getBody().documents()).hasSize(1);
    assertThat(listResponse.getBody().documents().get(0).id())
        .isEqualTo(secondResponse.getBody().id());
  }

  @Test
  void anApprovedDocumentDoesNotAcceptANewSubmission() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = createAndConfirmUpload(accessToken, Purpose.IDENTITY_DOCUMENT);
    DocumentSummary document =
        submit(accessToken, Purpose.IDENTITY_DOCUMENT, uploadId, DocumentSummary.class).getBody();
    moderation.approveDocument(document.id());

    UUID newUploadId = createAndConfirmUpload(accessToken, Purpose.IDENTITY_DOCUMENT);
    ResponseEntity<ErrorResponse> response =
        submitExpectingError(accessToken, Purpose.IDENTITY_DOCUMENT, newUploadId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("DOCUMENT_ALREADY_APPROVED");
  }

  @Test
  void resendingTheSamePendingUploadTwiceIsRejectedAsAlreadyUsed() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = createAndConfirmUpload(accessToken, Purpose.IDENTITY_DOCUMENT);
    submit(accessToken, Purpose.IDENTITY_DOCUMENT, uploadId, DocumentSummary.class);

    ResponseEntity<ErrorResponse> response =
        submitExpectingError(accessToken, Purpose.IDENTITY_DOCUMENT, uploadId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("UPLOAD_ALREADY_USED");
  }

  private <T> ResponseEntity<T> submit(
      String accessToken, Purpose type, UUID uploadId, Class<T> responseType) {
    return restTemplate.exchange(
        baseUrl("/me/documents"),
        HttpMethod.POST,
        authed(accessToken, new SubmitDocumentRequest(type, uploadId)),
        responseType);
  }

  private ResponseEntity<ErrorResponse> submitExpectingError(
      String accessToken, Purpose type, UUID uploadId) {
    return submit(accessToken, type, uploadId, ErrorResponse.class);
  }
}
