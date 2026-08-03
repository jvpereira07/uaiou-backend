package com.uaiou.blocks.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RN-07.1 — bloqueio é por estabelecimento e não afeta a relação do entregador com os demais
 * (V5__pedido_negociacao.sql).
 *
 * <p>Nasceu em T-11 só para leitura (o motor de elegibilidade precisa excluir o bloqueado, RF-11.5)
 * e ganhou escrita em T-12 — mesmo caminho que {@code Sancao} seguiu entre T-03 e T-07.
 *
 * <p>Sem mutadores: bloqueio não se edita, cria-se ou remove-se (RF-12.2/RF-12.8). Trocar o motivo
 * de um bloqueio existente não é um caso de uso que a doc preveja.
 */
@Entity
@Table(name = "bloqueio")
public class Bloqueio {

  @Id private UUID id;

  @Column(name = "estabelecimento_id")
  private UUID estabelecimentoId;

  @Column(name = "entregador_id")
  private UUID entregadorId;

  private String motivo;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected Bloqueio() {
    // exigido pela JPA
  }

  public Bloqueio(UUID id, UUID estabelecimentoId, UUID entregadorId, String motivo) {
    this.id = id;
    this.estabelecimentoId = estabelecimentoId;
    this.entregadorId = entregadorId;
    this.motivo = motivo;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEstabelecimentoId() {
    return estabelecimentoId;
  }

  public UUID getEntregadorId() {
    return entregadorId;
  }

  public String getMotivo() {
    return motivo;
  }
}
