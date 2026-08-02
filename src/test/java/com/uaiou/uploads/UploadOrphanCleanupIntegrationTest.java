package com.uaiou.uploads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.uploads.config.InternalMinioClient;
import com.uaiou.uploads.config.MinioProperties;
import com.uaiou.uploads.dto.CreateUploadResponse;
import com.uaiou.uploads.repository.UploadRepository;
import com.uaiou.uploads.service.OrphanUploadCleanupJob;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-05.7 — critério de aceite 8 de T-05. */
class UploadOrphanCleanupIntegrationTest extends AbstractAuthIntegrationTest {

  private static final byte[] VALID_JPEG_BYTES =
      Base64.getDecoder()
          .decode(
              "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAMCAgICAgMCAgIDAwMDBAYEBAQEBAgGBgUGCQgKCgkICQkKDA8MCgsOCwkJDRENDg8QEBEQCgwSExIQEw8QEBD/2wBDAQMDAwQDBAgEBAgQCwkLEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBD/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAj/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k=");

  @Autowired private OrphanUploadCleanupJob job;
  @Autowired private UploadRepository uploadRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InternalMinioClient internalMinioClient;
  @Autowired private MinioProperties minioProperties;

  @Test
  void removesExpiredAwaitingUploadsFromTheDatabaseAndTheBucket() {
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
    String objectKey = uploadRepository.findById(created.id()).orElseThrow().getObjectKey();

    backdateExpiry(created.id());
    job.removeExpiredAwaitingUploads();

    assertThat(uploadRepository.findById(created.id())).isEmpty();
    assertThatThrownBy(
            () ->
                internalMinioClient
                    .client()
                    .statObject(
                        StatObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(objectKey)
                            .build()))
        .isInstanceOf(ErrorResponseException.class);
  }

  @Test
  void neverTouchesAnUploadThatWasNeverConfirmedButHasNotExpiredYet() {
    RegisteredTestUser user = registerAndActivateCourier();
    String accessToken = login(user).accessToken();
    CreateUploadResponse created =
        createUpload(
                accessToken,
                Purpose.IDENTITY_DOCUMENT,
                "image/jpeg",
                (long) VALID_JPEG_BYTES.length)
            .getBody();

    job.removeExpiredAwaitingUploads();

    assertThat(uploadRepository.findById(created.id())).isPresent();
  }

  @Test
  void neverTouchesAReadyUploadEvenIfItsCreationRecordWouldOtherwiseLookExpired() {
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
    confirmUpload(accessToken, created.id(), Object.class);
    backdateExpiry(created.id());

    job.removeExpiredAwaitingUploads();

    assertThat(uploadRepository.findById(created.id())).isPresent();
  }

  private void backdateExpiry(UUID uploadId) {
    jdbcTemplate.update(
        "update upload set expira_em = now() - interval '1 hour' where id = ?", uploadId);
  }
}
