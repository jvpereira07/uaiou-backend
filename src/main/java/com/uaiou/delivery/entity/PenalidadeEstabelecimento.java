package com.uaiou.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RF-16.5/RN-09.3 — fato bruto de penalidade por contingência atribuída ao estabelecimento
 * (V18__contingencia_e_finalizacao.sql). Objetiva (telefone ausente + ausência de repasse), por
 * isso aplicada pelo job sem arbitragem.
 *
 * <p>T-16 só grava o evento. T-20 (score) agrega em taxa de contingência; T-22 expõe ao
 * estabelecimento (RF-16.8: transparência antes da punição) — mesmo padrão de {@code
 * RegistroAuditoria}: append-only, sem mutador além do construtor.
 */
@Entity
@Table(name = "penalidade_estabelecimento")
public class PenalidadeEstabelecimento {

  @Id private UUID id;

  @Column(name = "estabelecimento_id")
  private UUID estabelecimentoId;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  private String motivo;

  private int pontos;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected PenalidadeEstabelecimento() {
    // exigido pela JPA
  }

  public PenalidadeEstabelecimento(
      UUID id, UUID estabelecimentoId, UUID pedidoId, String motivo, int pontos) {
    this.id = id;
    this.estabelecimentoId = estabelecimentoId;
    this.pedidoId = pedidoId;
    this.motivo = motivo;
    this.pontos = pontos;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEstabelecimentoId() {
    return estabelecimentoId;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public String getMotivo() {
    return motivo;
  }

  public int getPontos() {
    return pontos;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
