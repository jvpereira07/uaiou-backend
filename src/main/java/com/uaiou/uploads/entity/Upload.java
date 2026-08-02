package com.uaiou.uploads.entity;

import com.uaiou.uploads.Purpose;
import com.uaiou.uploads.UploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Registro de upload em duas etapas (RF-05.1/05.3, V2__upload.sql): nasce {@code awaiting_upload}
 * com o caminho reservado antes do arquivo existir, e só vira {@code ready} depois que o servidor
 * lê o objeto de verdade no MinIO e confere tipo/tamanho reais — nunca confia no que foi declarado
 * na criação.
 */
@Entity
@Table(name = "upload")
public class Upload {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  private Purpose purpose;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "size_bytes")
  private long sizeBytes;

  private UploadStatus status;

  @Column(name = "object_key")
  private String objectKey;

  private String checksum;

  @Column(name = "expira_em")
  private Instant expiraEm;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected Upload() {
    // exigido pela JPA
  }

  public Upload(
      UUID id,
      UUID usuarioId,
      Purpose purpose,
      String contentType,
      long sizeBytes,
      String objectKey,
      Instant expiraEm) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.purpose = purpose;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.objectKey = objectKey;
    this.expiraEm = expiraEm;
    this.status = UploadStatus.AWAITING_UPLOAD;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public Purpose getPurpose() {
    return purpose;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public UploadStatus getStatus() {
    return status;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String getChecksum() {
    return checksum;
  }

  public Instant getExpiraEm() {
    return expiraEm;
  }

  public boolean isReady() {
    return status == UploadStatus.READY;
  }

  public boolean isAwaitingUpload() {
    return status == UploadStatus.AWAITING_UPLOAD;
  }

  public boolean isExpired() {
    return expiraEm.isBefore(Instant.now());
  }

  public boolean pertenceA(UUID usuarioId) {
    return this.usuarioId.equals(usuarioId);
  }

  /**
   * RF-05.3: o valor real lido do MinIO (tipo, tamanho, checksum) substitui o que foi só declarado
   * na criação — a partir daqui é isso, e não o {@code contentType}/{@code sizeBytes} originais,
   * que qualquer consumidor deve confiar.
   */
  public void confirmar(String realContentType, long realSizeBytes, String checksum) {
    this.contentType = realContentType;
    this.sizeBytes = realSizeBytes;
    this.checksum = checksum;
    this.status = UploadStatus.READY;
  }
}
