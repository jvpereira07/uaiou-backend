package com.uaiou.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Nunca criado por auto-registro (RF-03.1) — existe aqui só para leitura, quando outro módulo
 * precisar.
 */
@Entity
@Table(name = "admin")
public class Admin {

  @Id
  @Column(name = "usuario_id")
  private UUID usuarioId;

  @OneToOne
  @MapsId
  @JoinColumn(name = "usuario_id")
  private Usuario usuario;

  private String nivel;

  protected Admin() {
    // exigido pela JPA
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getNivel() {
    return nivel;
  }
}
