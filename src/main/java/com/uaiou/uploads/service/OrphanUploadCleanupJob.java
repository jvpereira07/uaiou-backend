package com.uaiou.uploads.service;

import com.uaiou.uploads.UploadStatus;
import com.uaiou.uploads.config.InternalMinioClient;
import com.uaiou.uploads.config.MinioProperties;
import com.uaiou.uploads.entity.Upload;
import com.uaiou.uploads.repository.UploadRepository;
import io.minio.RemoveObjectArgs;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-05.7: {@code awaiting_upload} vive só o suficiente para o cliente confirmar o envio (~15 min,
 * mesmo prazo de {@link UploadService#create}) — passado isso, o registro nunca mais vira {@code
 * ready} (o cliente teria que pedir uma URL nova), então fica só ocupando espaço no banco e,
 * possivelmente, um objeto órfão no bucket.
 */
@Component
public class OrphanUploadCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(OrphanUploadCleanupJob.class);

  private final UploadRepository uploadRepository;
  private final InternalMinioClient internalMinioClient;
  private final MinioProperties properties;

  public OrphanUploadCleanupJob(
      UploadRepository uploadRepository,
      InternalMinioClient internalMinioClient,
      MinioProperties properties) {
    this.uploadRepository = uploadRepository;
    this.internalMinioClient = internalMinioClient;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "PT15M")
  @Transactional
  public void removeExpiredAwaitingUploads() {
    List<Upload> expired =
        uploadRepository.findExpired(UploadStatus.AWAITING_UPLOAD, Instant.now());
    for (Upload upload : expired) {
      deleteObjectQuietly(upload.getObjectKey());
      uploadRepository.delete(upload);
    }
    if (!expired.isEmpty()) {
      log.info(
          "Coleta de órfãos: {} upload(s) awaiting_upload expirado(s) removido(s).",
          expired.size());
    }
  }

  private void deleteObjectQuietly(String objectKey) {
    try {
      internalMinioClient
          .client()
          .removeObject(
              RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception e) {
      // Comum: o cliente nunca chegou a enviar o arquivo, então o objeto nunca existiu no bucket —
      // não é
      // motivo para deixar o registro no banco.
      log.debug("Objeto \"{}\" não removido do MinIO (provavelmente nunca existiu).", objectKey, e);
    }
  }
}
