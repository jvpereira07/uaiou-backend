package com.uaiou.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "expira_em")
  private Instant expiraEm;

  @Column(name = "usado_em")
  private Instant usadoEm;

  protected PasswordResetToken() {
    // exigido pela JPA
  }

  public PasswordResetToken(UUID id, UUID usuarioId, String tokenHash, Instant expiraEm) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.tokenHash = tokenHash;
    this.expiraEm = expiraEm;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public boolean isValido() {
    return usadoEm == null && expiraEm.isAfter(Instant.now());
  }

  public void marcarUsado() {
    this.usadoEm = Instant.now();
  }
}
