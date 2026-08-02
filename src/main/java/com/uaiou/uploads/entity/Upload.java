package com.uaiou.uploads.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Mapeada aqui só para leitura (T-04 precisa validar posse e prontidão de um {@code uploadId}
 * recebido em {@code PATCH /me}); a escrita — criar e confirmar upload — é do módulo {@code
 * uploads} completo, em T-05, reusando esta mesma entidade (V2__upload.sql).
 */
@Entity
@Table(name = "upload")
public class Upload {

  private static final String STATUS_READY = "ready";

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  private String purpose;

  private String status;

  protected Upload() {
    // exigido pela JPA
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getPurpose() {
    return purpose;
  }

  public boolean isReady() {
    return STATUS_READY.equals(status);
  }

  public boolean pertenceA(UUID usuarioId) {
    return this.usuarioId.equals(usuarioId);
  }
}
