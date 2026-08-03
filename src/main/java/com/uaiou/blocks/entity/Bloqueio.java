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
 * <p>Mapeada aqui em T-11 só para LEITURA: o motor de elegibilidade precisa excluir o entregador
 * bloqueado (RF-11.5). A escrita — criar e remover bloqueio — é de T-12, que reusa esta mesma
 * entidade (mesmo caminho que {@code Sancao} seguiu entre T-03 e T-07).
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
