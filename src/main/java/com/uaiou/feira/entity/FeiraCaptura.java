package com.uaiou.feira.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Uma pessoa pegou um pedido da feira (V28__feira.sql). A regra "uma vez por pessoa por pedido"
 * mora na unique {@code ux_feira_captura}, não aqui: a disputa é concorrente e checar antes de
 * inserir perde a corrida.
 */
@Entity
@Table(name = "feira_captura")
public class FeiraCaptura {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "entregador_id")
  private UUID entregadorId;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  /** Nulo = capturou e ainda não retirou o prêmio. É o que o operador olha no balcão. */
  @Column(name = "entregue_em")
  private Instant entregueEm;

  /**
   * V29 — o pedido que esta captura gerou. É por ele que o jogador executa a entrega: a captura
   * vira um pedido atribuído e o fluxo do produto assume daí.
   */
  @Column(name = "pedido_gerado_id")
  private UUID pedidoGeradoId;

  protected FeiraCaptura() {
    // exigido pela JPA
  }

  public FeiraCaptura(UUID id, UUID pedidoId, UUID entregadorId, UUID pedidoGeradoId) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.entregadorId = entregadorId;
    this.pedidoGeradoId = pedidoGeradoId;
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

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getEntregueEm() {
    return entregueEm;
  }

  public UUID getPedidoGeradoId() {
    return pedidoGeradoId;
  }

  /** Idempotente: o operador clicar duas vezes não reescreve a hora da entrega. */
  public void marcarEntregue() {
    if (entregueEm == null) {
      entregueEm = Instant.now();
    }
  }
}
