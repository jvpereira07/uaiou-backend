package com.uaiou.delivery.entity;

import com.uaiou.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RF-18.7 — correção de {@link LancamentoFrete} sem reescrever o passado (RN-13.1/13.2): o
 * lançamento original permanece intacto, e este registro é a correção vinculada a ele, com a
 * `reference` obrigatória que separa correção legítima de mudança sem origem.
 *
 * <p>Append-only, mesmo padrão de {@code RegistroAuditoria}: sem mutador além do construtor.
 */
@Entity
@Table(name = "ajuste_lancamento_frete")
public class AjusteLancamentoFrete {

  @Id private UUID id;

  @Column(name = "lancamento_id")
  private UUID lancamentoId;

  @Column(name = "novo_valor")
  private Money novoValor;

  private String motivo;

  @Column(name = "referencia_tipo")
  private String referenciaTipo;

  @Column(name = "referencia_id")
  private UUID referenciaId;

  @Column(name = "admin_id")
  private UUID adminId;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected AjusteLancamentoFrete() {
    // exigido pela JPA
  }

  public AjusteLancamentoFrete(
      UUID id,
      UUID lancamentoId,
      Money novoValor,
      String motivo,
      String referenciaTipo,
      UUID referenciaId,
      UUID adminId) {
    this.id = id;
    this.lancamentoId = lancamentoId;
    this.novoValor = novoValor;
    this.motivo = motivo;
    this.referenciaTipo = referenciaTipo;
    this.referenciaId = referenciaId;
    this.adminId = adminId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getLancamentoId() {
    return lancamentoId;
  }

  public Money getNovoValor() {
    return novoValor;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
