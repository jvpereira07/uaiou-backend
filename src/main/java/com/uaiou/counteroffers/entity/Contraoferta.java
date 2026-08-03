package com.uaiou.counteroffers.entity;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Proposta de outro frete pelo entregador (V5__pedido_negociacao.sql). A criação e a decisão são de
 * T-14; T-13 só precisa <strong>invalidar</strong> as pendentes quando alguém aceita o pedido pelo
 * valor proposto (RF-13.4) — inclusive a do próprio aceitante, que deixou de fazer sentido.
 */
@Entity
@Table(name = "contraoferta")
public class Contraoferta {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "entregador_id")
  private UUID entregadorId;

  @Column(name = "valor_proposto")
  private Money valorProposto;

  private CounterofferStatus status;

  @Column(name = "respondido_em")
  private Instant respondidoEm;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected Contraoferta() {
    // exigido pela JPA
  }

  public Contraoferta(UUID id, UUID pedidoId, UUID entregadorId, Money valorProposto) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.entregadorId = entregadorId;
    this.valorProposto = valorProposto;
    this.status = CounterofferStatus.PENDING;
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

  public Money getValorProposto() {
    return valorProposto;
  }

  public CounterofferStatus getStatus() {
    return status;
  }

  /**
   * RF-13.4 — o pedido foi aceito por outro caminho; esta proposta perdeu o objeto. Distinto de
   * "recusada" (T-14): ninguém a avaliou, ela deixou de ser possível.
   */
  public void invalidar() {
    this.status = CounterofferStatus.INVALIDATED;
    this.respondidoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }
}
