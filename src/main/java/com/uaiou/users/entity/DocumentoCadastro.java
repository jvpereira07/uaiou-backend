package com.uaiou.users.entity;

import com.uaiou.uploads.Purpose;
import com.uaiou.users.DocumentApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Prova de um campo verificado (RF-04.3, T-04) ou de um documento exigido no cadastro (RF-06.2,
 * T-06). {@code tipo} reusa {@link Purpose} (T-05) — o vocabulário de "o que este documento
 * comprova" é o mesmo dos dois lados, {@code upload.purpose} e {@code documento_cadastro.tipo}, só
 * que este último é sempre um subconjunto (nunca {@code MERCHANT_LOGO}/{@code DELIVERY_PROOF}, que
 * não passam por moderação de cadastro) — quem garante isso é o serviço, não o tipo em si.
 *
 * <p>{@code motivo_rejeicao}/{@code avaliado_por}/{@code avaliado_em} continuam de fora: são
 * escritos pela decisão do admin (T-07); aqui só {@code motivo_rejeicao} é mapeado, para leitura
 * (RF-06.1).
 */
@Entity
@Table(name = "documento_cadastro")
public class DocumentoCadastro {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  private Purpose tipo;

  @Column(name = "upload_id")
  private UUID uploadId;

  @Column(name = "status_aprovacao")
  private DocumentApprovalStatus statusAprovacao;

  @Column(name = "motivo_rejeicao")
  private String motivoRejeicao;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected DocumentoCadastro() {
    // exigido pela JPA
  }

  public DocumentoCadastro(UUID id, UUID usuarioId, Purpose tipo, UUID uploadId) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.tipo = tipo;
    this.uploadId = uploadId;
    this.statusAprovacao = DocumentApprovalStatus.PENDING;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public Purpose getTipo() {
    return tipo;
  }

  public UUID getUploadId() {
    return uploadId;
  }

  public DocumentApprovalStatus getStatusAprovacao() {
    return statusAprovacao;
  }

  public String getMotivoRejeicao() {
    return motivoRejeicao;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  /**
   * RF-06.4/RF-06.5: substituído por um reenvio — nunca deletado, o histórico fica (critério de
   * aceite 5).
   */
  public void marcarSuperado() {
    this.statusAprovacao = DocumentApprovalStatus.SUPERSEDED;
  }
}
