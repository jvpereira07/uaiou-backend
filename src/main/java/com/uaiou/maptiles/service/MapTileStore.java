package com.uaiou.maptiles.service;

import com.uaiou.uploads.config.InternalMinioClient;
import com.uaiou.uploads.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Cache dos recursos de mapa no MinIO, sob o prefixo {@value #PREFIX} do bucket de uploads.
 *
 * <p>MinIO e não Redis: tile é binário, tem dezenas de KB e o conjunto cresce com a área rodada — é
 * disco, não memória. E o conjunto é compartilhado: o tile que um entregador baixou serve a todos
 * os outros que passarem pela mesma rua, e sobrevive a deploy.
 *
 * <p>Dividir o bucket é seguro: a limpeza de uploads órfãos parte das linhas do banco, nunca de uma
 * listagem do bucket, então não enxerga este prefixo.
 *
 * <p>Mesma disciplina do {@link com.uaiou.routing.service.RouteCache}: <strong>cache, nunca fonte
 * de verdade</strong>. MinIO fora do ar vira "não tenho" e o chamador vai ao provedor; nada aqui
 * lança.
 */
@Component
public class MapTileStore {

  private static final Logger log = LoggerFactory.getLogger(MapTileStore.class);

  static final String PREFIX = "map-tiles/";
  private static final String ENCODING_METADATA = "encoding";
  private static final String GZIP = "gzip";

  private final InternalMinioClient minio;
  private final String bucket;

  public MapTileStore(InternalMinioClient minio, MinioProperties minioProperties) {
    this.minio = minio;
    this.bucket = minioProperties.bucket();
  }

  public Optional<CachedResource> find(String key) {
    try (GetObjectResponse objeto =
        minio
            .client()
            .getObject(GetObjectArgs.builder().bucket(bucket).object(PREFIX + key).build())) {
      byte[] body = objeto.readAllBytes();
      String contentType = objeto.headers().get(HttpHeaders.CONTENT_TYPE);
      boolean gzip = GZIP.equals(objeto.headers().get("x-amz-meta-" + ENCODING_METADATA));
      return Optional.of(new CachedResource(body, contentType, gzip, storedAt(objeto)));
    } catch (ErrorResponseException e) {
      if (!"NoSuchKey".equals(e.errorResponse().code())) {
        log.warn(
            "Cache de mapa indisponível na leitura — seguindo para o provedor: {}", e.toString());
      }
      return Optional.empty();
    } catch (Exception e) {
      log.warn(
          "Cache de mapa indisponível na leitura — seguindo para o provedor: {}", e.toString());
      return Optional.empty();
    }
  }

  public void save(String key, CachedResource resource) {
    try {
      minio
          .client()
          .putObject(
              PutObjectArgs.builder().bucket(bucket).object(PREFIX + key).stream(
                      new ByteArrayInputStream(resource.body()), resource.body().length, -1)
                  .contentType(resource.contentType())
                  .userMetadata(Map.of(ENCODING_METADATA, resource.gzip() ? GZIP : "identity"))
                  .build());
    } catch (Exception e) {
      log.warn(
          "Cache de mapa indisponível na escrita — o recurso vale só para esta resposta: {}",
          e.toString());
    }
  }

  /** Sem {@code Last-Modified} legível, trata como antigo: a próxima leitura renova. */
  private Instant storedAt(GetObjectResponse objeto) {
    String lastModified = objeto.headers().get(HttpHeaders.LAST_MODIFIED);
    if (lastModified == null) {
      return Instant.EPOCH;
    }
    try {
      return ZonedDateTime.parse(lastModified, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
    } catch (RuntimeException e) {
      return Instant.EPOCH;
    }
  }
}
