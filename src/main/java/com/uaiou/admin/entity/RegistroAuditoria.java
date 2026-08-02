package com.uaiou.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RF-07.1/RF-07.2: trilha de auditoria — append-only por desenho
 * (V3__sessao_moderacao_auditoria.sql). Sem mutadores de propósito, além do construtor: nada nesta
 * classe deveria permitir alterar um registro já gravado. {@code referenciaTipo} é polimórfica
 * ("usuario", "sancao", ...), quem decide o que é referência válida é o serviço que chama {@link
 * com.uaiou.admin.service.AuditService#record}, não o schema.
 */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

  @Id private UUID id;

  @Column(name = "admin_id")
  private UUID adminId;

  private String acao;

  @Column(name = "referencia_tipo")
  private String referenciaTipo;

  @Column(name = "referencia_id")
  private UUID referenciaId;

  private String motivo;

  private BigDecimal valor;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected RegistroAuditoria() {
    // exigido pela JPA
  }

  public RegistroAuditoria(
      UUID id,
      UUID adminId,
      String acao,
      String referenciaTipo,
      UUID referenciaId,
      String motivo,
      BigDecimal valor) {
    this.id = id;
    this.adminId = adminId;
    this.acao = acao;
    this.referenciaTipo = referenciaTipo;
    this.referenciaId = referenciaId;
    this.motivo = motivo;
    this.valor = valor;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAdminId() {
    return adminId;
  }

  public String getAcao() {
    return acao;
  }

  public String getReferenciaTipo() {
    return referenciaTipo;
  }

  public UUID getReferenciaId() {
    return referenciaId;
  }

  public String getMotivo() {
    return motivo;
  }

  public BigDecimal getValor() {
    return valor;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
