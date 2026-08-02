package com.uaiou.uploads;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.uploads.dto.CreateUploadResponse;
import com.uaiou.uploads.dto.UploadMetadataResponse;
import com.uaiou.users.dto.PatchMeProfile;
import com.uaiou.users.dto.PatchMeRequest;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-05.5, RF-05.6 — critérios de aceite 6 e 7 de T-05. */
class UploadReadAndAuthorizationIntegrationTest extends AbstractAuthIntegrationTest {

  private static final byte[] VALID_JPEG_BYTES =
      Base64.getDecoder()
          .decode(
              "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/2wBDAQMDAwQDBAgEBAgQCwkLEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBD/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAj/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k=");

  @Test
  void ownerCanReadTheirOwnConfirmedUploadWithAFreshPresignedUrl() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = createAndConfirm(accessToken, Purpose.IDENTITY_DOCUMENT);

    ResponseEntity<UploadMetadataResponse> response = getMetadata(accessToken, uploadId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().status()).isEqualTo(UploadStatus.READY);
    assertThat(response.getBody().fileUrl()).isNotBlank();
  }

  @Test
  void metadataForAnAwaitingUploadHasNoFileUrlYet() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    CreateUploadResponse created =
        createUpload(
                accessToken,
                Purpose.IDENTITY_DOCUMENT,
                "image/jpeg",
                (long) VALID_JPEG_BYTES.length)
            .getBody();

    ResponseEntity<UploadMetadataResponse> response = getMetadata(accessToken, created.id());

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().status()).isEqualTo(UploadStatus.AWAITING_UPLOAD);
    assertThat(response.getBody().fileUrl()).isNull();
  }

  @Test
  void theSameUploadGetsAFreshPresignedUrlOnEachRead() throws InterruptedException {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    UUID uploadId = createAndConfirm(accessToken, Purpose.IDENTITY_DOCUMENT);

    String firstUrl = getMetadata(accessToken, uploadId).getBody().fileUrl();
    // A assinatura AWS SigV4 tem granularidade de segundo (X-Amz-Date) — duas chamadas no mesmo
    // segundo
    // produzem a mesma URL por coincidência de relógio, não porque algo ficou persistido/cacheado.
    Thread.sleep(1100);
    String secondUrl = getMetadata(accessToken, uploadId).getBody().fileUrl();

    // Assinatura/expiração mudam a cada chamada — provar "gerado a cada chamada, nunca persistido"
    // na
    // prática (RF-05.5), não só por leitura de código.
    assertThat(firstUrl).isNotEqualTo(secondUrl);
  }

  @Test
  void anotherUserCannotReadSomeoneElsesUpload() {
    RegisteredTestUser owner = registerAndActivateCourier();
    RegisteredTestUser stranger = registerAndActivateCourier();
    String ownerToken = login(owner).accessToken();
    String strangerToken = login(stranger).accessToken();
    UUID uploadId = createAndConfirm(ownerToken, Purpose.IDENTITY_DOCUMENT);

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/uploads/" + uploadId),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(strangerToken)),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void anotherUserCannotLinkSomeoneElsesUploadThroughARealConsumerEither() {
    RegisteredTestUser owner = registerAndActivateCourier();
    RegisteredTestUser stranger = registerAndActivateCourier();
    String ownerToken = login(owner).accessToken();
    String strangerToken = login(stranger).accessToken();
    UUID uploadId = createAndConfirm(ownerToken, Purpose.IDENTITY_DOCUMENT);

    PatchMeRequest request =
        new PatchMeRequest(
            null,
            null,
            new PatchMeProfile(
                "99988877766",
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
    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/me"), HttpMethod.PATCH, authed(strangerToken, request), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody().error().code()).isEqualTo("UPLOAD_NOT_FOUND");
  }

  private UUID createAndConfirm(String accessToken, Purpose purpose) {
    CreateUploadResponse created =
        createUpload(accessToken, purpose, "image/jpeg", (long) VALID_JPEG_BYTES.length).getBody();
    putBytes(created.uploadUrl(), VALID_JPEG_BYTES, "image/jpeg");
    confirmUpload(accessToken, created.id(), Object.class);
    return created.id();
  }

  private ResponseEntity<UploadMetadataResponse> getMetadata(String accessToken, UUID uploadId) {
    return restTemplate.exchange(
        baseUrl("/uploads/" + uploadId),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(accessToken)),
        UploadMetadataResponse.class);
  }
}
