package com.uaiou.feira.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Um jogador atingiu a meta de um bônus. "Uma vez por pessoa por bônus" mora na unique {@code
 * ux_feira_bonus_conquista}, como a captura.
 */
@Entity
@Table(name = "feira_bonus_conquista")
public class FeiraBonusConquista {

  @Id private UUID id;

  @Column(name = "bonus_id")
  private UUID bonusId;

  @Column(name = "entregador_id")
  private UUID entregadorId;

  @CreationTimestamp
  @Column(name = "conquistado_em")
  private Instant conquistadoEm;

  /** Nulo = ainda não retirou o prêmio no balcão. */
  @Column(name = "entregue_em")
  private Instant entregueEm;

  protected FeiraBonusConquista() {
    // exigido pela JPA
  }

  public FeiraBonusConquista(UUID id, UUID bonusId, UUID entregadorId) {
    this.id = id;
    this.bonusId = bonusId;
    this.entregadorId = entregadorId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getBonusId() {
    return bonusId;
  }

  public UUID getEntregadorId() {
    return entregadorId;
  }

  public Instant getConquistadoEm() {
    return conquistadoEm;
  }

  public Instant getEntregueEm() {
    return entregueEm;
  }

  /** Idempotente: dois cliques no balcão, uma hora de entrega. */
  public void marcarEntregue() {
    if (entregueEm == null) {
      entregueEm = Instant.now();
    }
  }
}
