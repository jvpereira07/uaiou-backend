package com.uaiou.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapeada aqui só para leitura (T-03 precisa saber por que um login foi barrado); a escrita —
 * aplicar e revogar sanção — é do módulo {@code admin}, em T-07, reusando esta mesma entidade.
 */
@Entity
@Table(name = "sancao")
public class Sancao {

  @Id private UUID id;

  @Column(name = "usuario_alvo_id")
  private UUID usuarioAlvoId;

  private String tipo;

  private String motivo;

  private Instant fim;

  private boolean ativa;

  protected Sancao() {
    // exigido pela JPA
  }

  public String getTipo() {
    return tipo;
  }

  public String getMotivo() {
    return motivo;
  }

  public Instant getFim() {
    return fim;
  }

  public boolean isBanimento() {
    return "banimento".equals(tipo);
  }
}
