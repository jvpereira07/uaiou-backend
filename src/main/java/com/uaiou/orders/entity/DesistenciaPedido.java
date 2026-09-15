package com.uaiou.orders.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RF-26.26 — registro da desistência (V24). O pedido volta limpo a "publicado"; o que aconteceu
 * fica aqui, com os marcos copiados do pedido no instante da desistência, porque depois dela o
 * pedido já não os tem.
 */
@Entity
@Table(name = "desistencia_pedido")
public class DesistenciaPedido {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "entregador_id")
  private UUID entregadorId;

  private String motivo;

  private String nota;

  @Column(name = "aceito_em")
  private Instant aceitoEm;

  @Column(name = "chegou_em")
  private Instant chegouEm;

  @Column(name = "conta_penalidade")
  private boolean contaPenalidade;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected DesistenciaPedido() {
    // exigido pela JPA
  }

  public DesistenciaPedido(
      UUID id,
      UUID pedidoId,
      UUID entregadorId,
      String motivo,
      String nota,
      Instant aceitoEm,
      Instant chegouEm,
      boolean contaPenalidade) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.entregadorId = entregadorId;
    this.motivo = motivo;
    this.nota = nota;
    this.aceitoEm = aceitoEm;
    this.chegouEm = chegouEm;
    this.contaPenalidade = contaPenalidade;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public UUID getEntregadorId() {
    return entregadorId;
  }

  public String getMotivo() {
    return motivo;
  }

  public String getNota() {
    return nota;
  }

  public Instant getAceitoEm() {
    return aceitoEm;
  }

  public Instant getChegouEm() {
    return chegouEm;
  }

  public boolean isContaPenalidade() {
    return contaPenalidade;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
