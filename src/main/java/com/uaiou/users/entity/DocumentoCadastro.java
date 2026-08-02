package com.uaiou.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Prova de um campo verificado (RF-04.3, T-04): editar CPF/CNPJ/veículo via {@code PATCH /me} não
 * altera o valor direto — cria uma linha aqui, pendente, e a fila de aprovação (T-07) decide.
 * Mapeada apenas com o necessário para T-04 escrever; {@code motivo_rejeicao}/{@code
 * avaliado_por}/{@code avaliado_em} são do lado de decisão do admin (T-07), que os adiciona quando
 * existir.
 */
@Entity
@Table(name = "documento_cadastro")
public class DocumentoCadastro {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  private String tipo;

  @Column(name = "upload_id")
  private UUID uploadId;

  @Column(name = "status_aprovacao")
  private String statusAprovacao;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected DocumentoCadastro() {
    // exigido pela JPA
  }

  public DocumentoCadastro(UUID id, UUID usuarioId, String tipo, UUID uploadId) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.tipo = tipo;
    this.uploadId = uploadId;
    this.statusAprovacao = "pendente";
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getTipo() {
    return tipo;
  }

  public UUID getUploadId() {
    return uploadId;
  }

  public String getStatusAprovacao() {
    return statusAprovacao;
  }
}
