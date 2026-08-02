package com.uaiou.uploads.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Garante que o bucket exista antes da primeira requisição (RF-05.8) — sem política de leitura
 * anônima aplicada (o SDK cria buckets privados por padrão; nenhuma chamada extra de policy é
 * necessária).
 */
@Component
public class MinioBucketInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(MinioBucketInitializer.class);

  private final InternalMinioClient internalMinioClient;
  private final MinioProperties properties;

  public MinioBucketInitializer(
      InternalMinioClient internalMinioClient, MinioProperties properties) {
    this.internalMinioClient = internalMinioClient;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    boolean exists =
        internalMinioClient
            .client()
            .bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
    if (!exists) {
      internalMinioClient
          .client()
          .makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
      log.info("Bucket MinIO \"{}\" criado.", properties.bucket());
    }
  }
}
