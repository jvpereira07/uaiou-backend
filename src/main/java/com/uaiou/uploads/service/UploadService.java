package com.uaiou.uploads.service;

import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.uploads.Purpose;
import com.uaiou.uploads.PurposeConverter;
import com.uaiou.uploads.PurposePolicy;
import com.uaiou.uploads.UploadStatus;
import com.uaiou.uploads.config.InternalMinioClient;
import com.uaiou.uploads.config.MinioProperties;
import com.uaiou.uploads.config.PublicMinioClient;
import com.uaiou.uploads.dto.ConfirmUploadResponse;
import com.uaiou.uploads.dto.CreateUploadRequest;
import com.uaiou.uploads.dto.CreateUploadResponse;
import com.uaiou.uploads.dto.UploadMetadataResponse;
import com.uaiou.uploads.entity.Upload;
import com.uaiou.uploads.repository.UploadRepository;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-05.1 a RF-05.6 — fluxo de upload em duas etapas com validação real contra o MinIO. */
@Service
public class UploadService {

  private static final Logger log = LoggerFactory.getLogger(UploadService.class);

  private static final DateTimeFormatter PATH_DATE =
      DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);
  private static final long CREATE_URL_EXPIRY_MINUTES = 15;
  private static final long READ_URL_EXPIRY_MINUTES = 5;

  /**
   * Foto de perfil e logo aparecem em listas e avisos que ficam abertos na tela: 5 minutos venceria
   * a imagem antes de o usuário terminar de olhar para ela.
   */
  private static final long IMAGE_URL_EXPIRY_MINUTES = 60;

  /**
   * URL de leitura de uma imagem de perfil já vinculada (foto do entregador, logo do
   * estabelecimento). Nula quando não há imagem — quem chama não precisa testar antes.
   */
  public String urlDeImagem(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return null;
    }
    return presignedUrl(publicMinioClient, objectKey, Method.GET, IMAGE_URL_EXPIRY_MINUTES);
  }

  private final UploadRepository uploadRepository;
  private final InternalMinioClient internalMinioClient;
  private final PublicMinioClient publicMinioClient;
  private final MinioProperties properties;
  private final PurposeConverter purposeConverter = new PurposeConverter();

  public UploadService(
      UploadRepository uploadRepository,
      InternalMinioClient internalMinioClient,
      PublicMinioClient publicMinioClient,
      MinioProperties properties) {
    this.uploadRepository = uploadRepository;
    this.internalMinioClient = internalMinioClient;
    this.publicMinioClient = publicMinioClient;
    this.properties = properties;
  }

  @Transactional
  public CreateUploadResponse create(UUID usuarioId, CreateUploadRequest request) {
    PurposePolicy.Policy policy = PurposePolicy.of(request.purpose());
    if (!policy.allows(request.contentType())) {
      throw new BadRequestException(
          "CONTENT_TYPE_NOT_ALLOWED",
          "\"" + request.contentType() + "\" não é aceito para este propósito.");
    }
    if (request.sizeBytes() > policy.maxSizeBytes()) {
      throw new BadRequestException(
          "FILE_TOO_LARGE",
          "Tamanho declarado excede o máximo de "
              + policy.maxSizeBytes()
              + " bytes para este propósito.");
    }

    UUID id = UuidV7.next();
    String objectKey = buildObjectKey(request.purpose(), id);
    Instant expiraEm = Instant.now().plus(CREATE_URL_EXPIRY_MINUTES, ChronoUnit.MINUTES);

    Upload upload =
        new Upload(
            id,
            usuarioId,
            request.purpose(),
            request.contentType(),
            request.sizeBytes(),
            objectKey,
            expiraEm);
    uploadRepository.save(upload);

    String uploadUrl =
        presignedUrl(publicMinioClient, objectKey, Method.PUT, CREATE_URL_EXPIRY_MINUTES);

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("confirmation", new LinkRef("/api/v1/uploads/" + id, "PUT"));

    return new CreateUploadResponse(
        id, UploadStatus.AWAITING_UPLOAD, uploadUrl, expiraEm, policy.maxSizeBytes(), links);
  }

  @Transactional
  public ConfirmUploadResponse confirm(UUID usuarioId, UUID uploadId) {
    Upload upload = requireOwnedUpload(uploadId, usuarioId);
    if (!upload.isAwaitingUpload()) {
      // Já confirmado (ready) ou não existe mais estado intermediário — confirmar de novo não é o
      // caso de
      // uso; RF-05.4 trata "vincular um awaiting_upload" como 422, mesmo raciocínio de "não tem
      // prova".
      throw new BusinessRuleException(
          "UPLOAD_ALREADY_CONFIRMED", "Este upload já foi confirmado.", "RF-05.3");
    }

    PurposePolicy.Policy policy = PurposePolicy.of(upload.getPurpose());
    byte[] content = readRealObjectOrFail(upload, policy);

    String realContentType = new org.apache.tika.Tika().detect(content);
    if (!policy.allows(realContentType)) {
      log.warn(
          "Upload {} rejeitado: MIME real \"{}\" não permitido para {}.",
          uploadId,
          realContentType,
          upload.getPurpose());
      deleteObjectQuietly(upload.getObjectKey());
      throw new BusinessRuleException(
          "UPLOAD_VALIDATION_FAILED",
          "O conteúdo real do arquivo não corresponde a um tipo permitido.",
          "RF-05.3");
    }

    String checksum = sha256(content);
    upload.confirmar(realContentType, content.length, checksum);
    uploadRepository.save(upload);

    String fileUrl =
        presignedUrl(publicMinioClient, upload.getObjectKey(), Method.GET, READ_URL_EXPIRY_MINUTES);

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/uploads/" + uploadId));

    return new ConfirmUploadResponse(upload.getId(), upload.getStatus(), fileUrl, checksum, links);
  }

  @Transactional(readOnly = true)
  public UploadMetadataResponse readMetadata(
      UUID usuarioId, boolean requesterIsAdmin, UUID uploadId) {
    Upload upload = uploadRepository.findById(uploadId).orElseThrow(this::notFound);
    if (!requesterIsAdmin && !upload.pertenceA(usuarioId)) {
      // RF-05.6: nem existência é revelada a quem não é dono nem admin.
      throw notFound();
    }

    String fileUrl =
        upload.isReady()
            ? presignedUrl(
                publicMinioClient, upload.getObjectKey(), Method.GET, READ_URL_EXPIRY_MINUTES)
            : null;

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/uploads/" + uploadId));

    return new UploadMetadataResponse(
        upload.getId(),
        upload.getStatus(),
        upload.getPurpose(),
        upload.getContentType(),
        upload.getSizeBytes(),
        fileUrl,
        links);
  }

  /**
   * RF-05.4 — a "operação de vínculo" que outros módulos chamam em vez de reimplementar a checagem:
   * existe, pertence ao usuário, está {@code ready} e é do propósito esperado. O que falta para
   * "vínculo completo" (nunca reusado) é responsabilidade de quem chama — só o consumidor sabe em
   * qual tabela própria checar isso (ex.: {@code documento_cadastro.upload_id} em T-04), este
   * módulo não tem como saber.
   */
  @Transactional(readOnly = true)
  public Upload validateForConsumption(UUID uploadId, UUID usuarioId, Purpose expectedPurpose) {
    Upload upload = requireOwnedUpload(uploadId, usuarioId);
    if (!upload.isReady()) {
      throw new BusinessRuleException(
          "UPLOAD_NOT_READY", "O upload ainda não foi confirmado.", "RF-05.4");
    }
    if (upload.getPurpose() != expectedPurpose) {
      throw new BadRequestException(
          "UPLOAD_PURPOSE_MISMATCH",
          "Este upload não corresponde ao documento esperado para este campo.");
    }
    return upload;
  }

  private Upload requireOwnedUpload(UUID uploadId, UUID usuarioId) {
    Upload upload = uploadRepository.findById(uploadId).orElseThrow(this::notFound);
    if (!upload.pertenceA(usuarioId)) {
      throw notFound();
    }
    return upload;
  }

  private NotFoundException notFound() {
    return new NotFoundException("UPLOAD_NOT_FOUND", "Upload não encontrado.");
  }

  private byte[] readRealObjectOrFail(Upload upload, PurposePolicy.Policy policy) {
    try {
      StatObjectResponse stat =
          internalMinioClient
              .client()
              .statObject(
                  StatObjectArgs.builder()
                      .bucket(properties.bucket())
                      .object(upload.getObjectKey())
                      .build());
      if (stat.size() > policy.maxSizeBytes()) {
        deleteObjectQuietly(upload.getObjectKey());
        throw new BusinessRuleException(
            "UPLOAD_VALIDATION_FAILED",
            "O arquivo enviado excede o tamanho máximo permitido.",
            "RF-05.3");
      }
      try (InputStream in =
          internalMinioClient
              .client()
              .getObject(
                  GetObjectArgs.builder()
                      .bucket(properties.bucket())
                      .object(upload.getObjectKey())
                      .build())) {
        return in.readAllBytes();
      }
    } catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code())) {
        throw new BusinessRuleException(
            "UPLOAD_VALIDATION_FAILED",
            "Nenhum arquivo foi enviado para este upload ainda.",
            "RF-05.3");
      }
      throw new IllegalStateException("Falha ao ler objeto do MinIO", e);
    } catch (BusinessRuleException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao ler objeto do MinIO", e);
    }
  }

  private void deleteObjectQuietly(String objectKey) {
    try {
      internalMinioClient
          .client()
          .removeObject(
              RemoveObjectArgs.builder().bucket(properties.bucket()).object(objectKey).build());
    } catch (Exception e) {
      log.warn("Falha ao remover objeto \"{}\" do MinIO após validação rejeitada.", objectKey, e);
    }
  }

  private String presignedUrl(
      PublicMinioClient client, String objectKey, Method method, long expiryMinutes) {
    try {
      return client
          .client()
          .getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(method)
                  .bucket(properties.bucket())
                  .object(objectKey)
                  .expiry((int) expiryMinutes, TimeUnit.MINUTES)
                  .build());
    } catch (Exception e) {
      throw new IllegalStateException("Falha ao gerar URL pré-assinada do MinIO", e);
    }
  }

  private String buildObjectKey(Purpose purpose, UUID id) {
    String purposeSlug = purposeConverter.convertToDatabaseColumn(purpose);
    return purposeSlug + "/" + PATH_DATE.format(Instant.now()) + "/" + id;
  }

  private String sha256(byte[] content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
      return "sha256:" + HexFormat.of().formatHex(digest);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível na JVM", e);
    }
  }
}
