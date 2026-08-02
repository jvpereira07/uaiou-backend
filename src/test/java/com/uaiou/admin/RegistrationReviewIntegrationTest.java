package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.dto.AuditLogEntry;
import com.uaiou.admin.dto.PendingRegistrationSummary;
import com.uaiou.admin.dto.RegistrationReviewResponse;
import com.uaiou.admin.dto.ReviewDecision;
import com.uaiou.admin.dto.ReviewRegistrationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.uploads.Purpose;
import com.uaiou.users.DocumentApprovalStatus;
import com.uaiou.users.UserStatus;
import com.uaiou.users.dto.DocumentSummary;
import com.uaiou.users.dto.DocumentsResponse;
import com.uaiou.users.dto.SubmitDocumentRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * RF-07.3/RF-07.4 — critérios de aceite 1 (parcial — ver nota), 2, 3 de T-07.
 *
 * <p>Critério 1 ("pendente recebe 403 ao publicar pedido/ficar disponível; após aprovação passa")
 * não é totalmente verificável aqui: essas rotas são de T-10/T-11, que ainda não existem. O que
 * este teste garante é a parte que T-07 realmente entrega — a transição de {@code usuario.status}
 * que aquelas rotas vão consumir depois.
 */
class RegistrationReviewIntegrationTest extends AbstractAuthIntegrationTest {

  @Test
  void freshlyRegisteredCourierIsNotInTheQueueYet() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerCourier();

    PageResponse<PendingRegistrationSummary> queue = getQueue(admin.accessToken());

    assertThat(queue.data()).noneMatch(r -> r.userId().equals(courier.id()));
  }

  @Test
  void courierWithAllRequiredDocumentsAppearsInTheQueueWithSignedUrls() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerCourier();
    String courierToken = login(courier).accessToken();
    submitAllRequiredCourierDocuments(courierToken);

    PageResponse<PendingRegistrationSummary> queue = getQueue(admin.accessToken());

    PendingRegistrationSummary entry =
        queue.data().stream()
            .filter(r -> r.userId().equals(courier.id()))
            .findFirst()
            .orElseThrow();
    assertThat(entry.documents()).hasSize(3);
    assertThat(entry.documents()).allSatisfy(doc -> assertThat(doc.fileUrl()).isNotBlank());
  }

  @Test
  void approvingACompleteRegistrationActivatesTheAccountAndApprovesEveryDocument() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerCourier();
    String courierToken = login(courier).accessToken();
    submitAllRequiredCourierDocuments(courierToken);

    ResponseEntity<RegistrationReviewResponse> response =
        review(
            admin.accessToken(),
            courier.id(),
            new ReviewRegistrationRequest(ReviewDecision.APPROVED, "Tudo certo.", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().status()).isEqualTo(UserStatus.ACTIVE);

    DocumentsResponse documents = getMyDocuments(courierToken);
    assertThat(documents.documents())
        .allSatisfy(d -> assertThat(d.status()).isEqualTo(DocumentApprovalStatus.APPROVED));

    List<AuditLogEntry> auditRows = auditLogsFor(admin.accessToken(), courier.id());
    assertThat(auditRows).hasSize(1);
    assertThat(auditRows.get(0).action()).isEqualTo("aprovar_cadastro");
  }

  @Test
  void approvingAnIncompleteRegistrationIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerCourier();
    // Só 1 dos 3 documentos exigidos — nunca deveria aparecer na fila, mas o endpoint de decisão
    // também precisa se defender por conta própria (chamado direto, sem passar pela fila).
    String courierToken = login(courier).accessToken();
    UUID uploadId = createAndConfirmUpload(courierToken, Purpose.IDENTITY_DOCUMENT);
    submitDocument(courierToken, Purpose.IDENTITY_DOCUMENT, uploadId);

    ResponseEntity<ErrorResponse> response =
        reviewExpectingError(
            admin.accessToken(),
            courier.id(),
            new ReviewRegistrationRequest(ReviewDecision.APPROVED, "Aprovado sem checar.", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("REGISTRATION_INCOMPLETE");
  }

  @Test
  void rejectingWithoutDocumentIdsIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerCourier();
    String courierToken = login(courier).accessToken();
    submitAllRequiredCourierDocuments(courierToken);

    ResponseEntity<ErrorResponse> response =
        reviewExpectingError(
            admin.accessToken(),
            courier.id(),
            new ReviewRegistrationRequest(ReviewDecision.REJECTED, "CNH ilegível.", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("MISSING_FIELD");
  }

  @Test
  void rejectingCitedDocumentsReturnsTheReasonAndKeepsTheAccountRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerCourier();
    String courierToken = login(courier).accessToken();
    List<UUID> documentIds = submitAllRequiredCourierDocuments(courierToken);
    UUID citedDocumentId = documentIds.get(0);

    ResponseEntity<RegistrationReviewResponse> response =
        review(
            admin.accessToken(),
            courier.id(),
            new ReviewRegistrationRequest(
                ReviewDecision.REJECTED, "CNH ilegível.", List.of(citedDocumentId)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().status()).isEqualTo(UserStatus.REJECTED);

    DocumentsResponse documents = getMyDocuments(courierToken);
    var rejected =
        documents.documents().stream()
            .filter(d -> d.id().equals(citedDocumentId))
            .findFirst()
            .orElseThrow();
    assertThat(rejected.status()).isEqualTo(DocumentApprovalStatus.REJECTED);
    assertThat(rejected.rejectionReason()).isEqualTo("CNH ilegível.");

    List<AuditLogEntry> auditRows = auditLogsFor(admin.accessToken(), courier.id());
    assertThat(auditRows).hasSize(1);
    assertThat(auditRows.get(0).action()).isEqualTo("rejeitar_cadastro");
  }

  @Test
  void citingADocumentThatDoesNotBelongToTheRegistrationIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerCourier();
    String courierToken = login(courier).accessToken();
    submitAllRequiredCourierDocuments(courierToken);

    ResponseEntity<ErrorResponse> response =
        reviewExpectingError(
            admin.accessToken(),
            courier.id(),
            new ReviewRegistrationRequest(
                ReviewDecision.REJECTED, "Motivo qualquer.", List.of(UUID.randomUUID())));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("DOCUMENT_NOT_FOUND");
  }

  @Test
  void reviewingAnAlreadyActiveRegistrationIsRejected() {
    AdminSession admin = loginAdmin();
    RegisteredTestUser courier = registerAndActivateCourier();

    ResponseEntity<ErrorResponse> response =
        reviewExpectingError(
            admin.accessToken(),
            courier.id(),
            new ReviewRegistrationRequest(ReviewDecision.APPROVED, "Já ativo.", null));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("REGISTRATION_NOT_PENDING");
  }

  private List<UUID> submitAllRequiredCourierDocuments(String courierToken) {
    return List.of(
        submitDocument(
            courierToken,
            Purpose.IDENTITY_DOCUMENT,
            createAndConfirmUpload(courierToken, Purpose.IDENTITY_DOCUMENT)),
        submitDocument(
            courierToken,
            Purpose.VEHICLE_DOCUMENT,
            createAndConfirmUpload(courierToken, Purpose.VEHICLE_DOCUMENT)),
        submitDocument(
            courierToken,
            Purpose.DRIVER_LICENSE,
            createAndConfirmUpload(courierToken, Purpose.DRIVER_LICENSE)));
  }

  private UUID submitDocument(String accessToken, Purpose type, UUID uploadId) {
    ResponseEntity<DocumentSummary> response =
        restTemplate.exchange(
            baseUrl("/me/documents"),
            HttpMethod.POST,
            authed(accessToken, new SubmitDocumentRequest(type, uploadId)),
            DocumentSummary.class);
    return response.getBody().id();
  }

  private DocumentsResponse getMyDocuments(String accessToken) {
    return restTemplate
        .exchange(
            baseUrl("/me/documents"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(accessToken)),
            DocumentsResponse.class)
        .getBody();
  }

  private PageResponse<PendingRegistrationSummary> getQueue(String adminToken) {
    return restTemplate
        .exchange(
            baseUrl("/admin/registrations?status=pending"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(adminToken)),
            new ParameterizedTypeReference<PageResponse<PendingRegistrationSummary>>() {})
        .getBody();
  }

  private ResponseEntity<RegistrationReviewResponse> review(
      String adminToken, UUID userId, ReviewRegistrationRequest request) {
    return restTemplate.exchange(
        baseUrl("/admin/registrations/" + userId + "/review"),
        HttpMethod.PUT,
        authed(adminToken, request),
        RegistrationReviewResponse.class);
  }

  private ResponseEntity<ErrorResponse> reviewExpectingError(
      String adminToken, UUID userId, ReviewRegistrationRequest request) {
    return restTemplate.exchange(
        baseUrl("/admin/registrations/" + userId + "/review"),
        HttpMethod.PUT,
        authed(adminToken, request),
        ErrorResponse.class);
  }

  private List<AuditLogEntry> auditLogsFor(String adminToken, UUID referenceId) {
    String url =
        UriComponentsBuilder.fromUriString(baseUrl("/admin/audit-logs"))
            .queryParam("referenceId", referenceId)
            .toUriString();
    PageResponse<AuditLogEntry> page =
        restTemplate
            .exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                new ParameterizedTypeReference<PageResponse<AuditLogEntry>>() {})
            .getBody();
    return page.data();
  }
}
