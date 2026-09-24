package com.uaiou.feira.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Bônus da feira (V32__feira_bonus.sql): "complete {@code meta} entregas e ganhe {@code
 * recompensa}". Cadastrado pelo operador no painel, conquistado uma vez por jogador.
 */
@Entity
@Table(name = "feira_bonus")
public class FeiraBonus {

  @Id private UUID id;

  @Column(name = "titulo")
  private String titulo;

  @Column(name = "meta")
  private int meta;

  @Column(name = "recompensa")
  private String recompensa;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @Column(name = "encerrado_em")
  private Instant encerradoEm;

  protected FeiraBonus() {
    // exigido pela JPA
  }

  public FeiraBonus(UUID id, String titulo, int meta, String recompensa) {
    this.id = id;
    this.titulo = titulo;
    this.meta = meta;
    this.recompensa = recompensa;
  }

  public UUID getId() {
    return id;
  }

  public String getTitulo() {
    return titulo;
  }

  public int getMeta() {
    return meta;
  }

  public String getRecompensa() {
    return recompensa;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getEncerradoEm() {
    return encerradoEm;
  }

  public boolean isAtivo() {
    return encerradoEm == null;
  }

  /** Idempotente: encerrar de novo não reescreve a hora. */
  public void encerrar() {
    if (encerradoEm == null) {
      encerradoEm = Instant.now();
    }
  }
}
