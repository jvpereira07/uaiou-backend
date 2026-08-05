package com.uaiou.delivery.entity;

import com.uaiou.delivery.FinalizationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Prova da finalização — 1:1 com o pedido (V7__validacao_entrega.sql). RN-10.2: foto obrigatória no
 * modo contestável, ausente no modo por código (o geofence + o próprio código já são a prova).
 *
 * <p>{@code revisaoNecessaria}/{@code divergenciaMetros} — RF-15.7: antifraude que MARCA em vez de
 * BLOQUEAR. GPS ruim produz falso positivo com frequência alta demais para travar um entregador
 * honesto na porta do cliente; a divergência fica registrada para o suporte julgar com contexto.
 *
 * <p>Sem mutadores além do construtor: é evidência, não deveria mudar depois de gravada.
 */
@Entity
@Table(name = "evidencia_entrega")
public class EvidenciaEntrega {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "tipo_finalizacao")
  private FinalizationType tipoFinalizacao;

  @Column(name = "upload_id")
  private UUID uploadId;

  private BigDecimal lat;

  @Column(name = "long")
  private BigDecimal longitude;

  @Column(name = "revisao_necessaria")
  private boolean revisaoNecessaria;

  @Column(name = "divergencia_metros")
  private BigDecimal divergenciaMetros;

  @CreationTimestamp
  @Column(name = "registrado_em")
  private Instant registradoEm;

  protected EvidenciaEntrega() {
    // exigido pela JPA
  }

  public EvidenciaEntrega(
      UUID id,
      UUID pedidoId,
      FinalizationType tipoFinalizacao,
      UUID uploadId,
      BigDecimal lat,
      BigDecimal longitude,
      boolean revisaoNecessaria,
      BigDecimal divergenciaMetros) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.tipoFinalizacao = tipoFinalizacao;
    this.uploadId = uploadId;
    this.lat = lat;
    this.longitude = longitude;
    this.revisaoNecessaria = revisaoNecessaria;
    this.divergenciaMetros = divergenciaMetros;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public FinalizationType getTipoFinalizacao() {
    return tipoFinalizacao;
  }

  public UUID getUploadId() {
    return uploadId;
  }

  public boolean isRevisaoNecessaria() {
    return revisaoNecessaria;
  }

  public Instant getRegistradoEm() {
    return registradoEm;
  }
}
