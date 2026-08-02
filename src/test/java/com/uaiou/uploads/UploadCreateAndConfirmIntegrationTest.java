package com.uaiou.uploads;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.uploads.dto.ConfirmUploadResponse;
import com.uaiou.uploads.dto.CreateUploadRequest;
import com.uaiou.uploads.dto.CreateUploadResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-05.1 a RF-05.3 — critérios de aceite 1 a 4 de T-05. */
class UploadCreateAndConfirmIntegrationTest extends AbstractAuthIntegrationTest {

  // Fixture de 1x1 pixel JPEG válido — Tika precisa de bytes reais para detectar image/jpeg de
  // verdade
  // (RF-05.3: o servidor não confia no content-type declarado, então o teste não pode confiar nele
  // também).
  private static final byte[] VALID_JPEG_BYTES =
      Base64.getDecoder()
          .decode(
              "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/2wBDAQMDAwQDBAgEBAgQCwkLEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBD/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAj/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k=");

  @Test
  void createReturnsAPresignedUrlThatAcceptsThePutAndExpiresAsConfigured() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();

    ResponseEntity<CreateUploadResponse> created =
        createUpload(
            accessToken, Purpose.IDENTITY_DOCUMENT, "image/jpeg", (long) VALID_JPEG_BYTES.length);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    CreateUploadResponse body = created.getBody();
    assertThat(body.status()).isEqualTo(UploadStatus.AWAITING_UPLOAD);
    assertThat(body.uploadUrl()).isNotBlank();
    assertThat(body.expiresAt()).isAfter(Instant.now());
    assertThat(body.maxSizeBytes()).isEqualTo(5L * 1024 * 1024);

    ResponseEntity<Void> putResponse = putBytes(body.uploadUrl(), VALID_JPEG_BYTES, "image/jpeg");
    assertThat(putResponse.getStatusCode().is2xxSuccessful()).isTrue();
  }

  @Test
  void declaringASizeAboveTheMaximumIsRejectedAtCreation() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();

    CreateUploadRequest request =
        new CreateUploadRequest(Purpose.IDENTITY_DOCUMENT, "image/jpeg", 6L * 1024 * 1024);
    ResponseEntity<ErrorResponse> response =
        restTemplate.postForEntity(
            baseUrl("/uploads"), authed(accessToken, request), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().error().code()).isEqualTo("FILE_TOO_LARGE");
  }

  @Test
  void confirmingWithoutHavingUploadedAnythingFails() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    CreateUploadResponse created =
        createUpload(
                accessToken,
                Purpose.IDENTITY_DOCUMENT,
                "image/jpeg",
                (long) VALID_JPEG_BYTES.length)
            .getBody();

    ResponseEntity<ErrorResponse> response =
        confirmUpload(accessToken, created.id(), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("UPLOAD_VALIDATION_FAILED");
  }

  @Test
  void confirmingAFileWhoseRealContentDoesNotMatchTheDeclaredMimeFails() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    byte[] plainTextDisguisedAsJpeg = "isto nao e um jpeg de verdade, so texto puro".getBytes();

    CreateUploadResponse created =
        createUpload(
                accessToken,
                Purpose.IDENTITY_DOCUMENT,
                "image/jpeg",
                (long) plainTextDisguisedAsJpeg.length)
            .getBody();
    putBytes(created.uploadUrl(), plainTextDisguisedAsJpeg, "image/jpeg");

    ResponseEntity<ErrorResponse> response =
        confirmUpload(accessToken, created.id(), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("UPLOAD_VALIDATION_FAILED");
  }

  @Test
  void confirmingAFileLargerThanTheRealMaximumFailsEvenIfTheDeclaredSizeWasSmall() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    // Declara um tamanho pequeno (dentro do limite) mas realmente envia mais que o máximo —
    // RF-05.3: o
    // servidor confere o tamanho real, não confia no declarado.
    byte[] oversized = new byte[6 * 1024 * 1024];
    new Random().nextBytes(oversized);

    CreateUploadResponse created =
        createUpload(accessToken, Purpose.IDENTITY_DOCUMENT, "image/jpeg", 100L).getBody();
    putBytes(created.uploadUrl(), oversized, "image/jpeg");

    ResponseEntity<ErrorResponse> response =
        confirmUpload(accessToken, created.id(), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("UPLOAD_VALIDATION_FAILED");
  }

  @Test
  void happyPathConfirmMarksReadyWithChecksumAndFileUrl() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    CreateUploadResponse created =
        createUpload(
                accessToken,
                Purpose.IDENTITY_DOCUMENT,
                "image/jpeg",
                (long) VALID_JPEG_BYTES.length)
            .getBody();
    putBytes(created.uploadUrl(), VALID_JPEG_BYTES, "image/jpeg");

    ResponseEntity<ConfirmUploadResponse> response =
        confirmUpload(accessToken, created.id(), ConfirmUploadResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ConfirmUploadResponse body = response.getBody();
    assertThat(body.status()).isEqualTo(UploadStatus.READY);
    assertThat(body.checksum()).startsWith("sha256:");
    assertThat(body.fileUrl()).isNotBlank();
  }
}
