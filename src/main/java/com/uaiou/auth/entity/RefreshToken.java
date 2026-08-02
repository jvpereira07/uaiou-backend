package com.uaiou.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Só o hash do token é persistido (RF-03.2, mesmo princípio de senha). {@code familiaId} agrupa
 * todos os refresh tokens nascidos da mesma sessão original — reuso de um token já revogado revoga
 * a família inteira (RF-03.8), sinal de vazamento.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "familia_id")
  private UUID familiaId;

  @Column(name = "expira_em")
  private Instant expiraEm;

  @Column(name = "revogado_em")
  private Instant revogadoEm;

  protected RefreshToken() {
    // exigido pela JPA
  }

  public RefreshToken(UUID id, UUID usuarioId, String tokenHash, UUID familiaId, Instant expiraEm) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.tokenHash = tokenHash;
    this.familiaId = familiaId;
    this.expiraEm = expiraEm;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public UUID getFamiliaId() {
    return familiaId;
  }

  public Instant getExpiraEm() {
    return expiraEm;
  }

  public Instant getRevogadoEm() {
    return revogadoEm;
  }

  public boolean isValido() {
    return revogadoEm == null && expiraEm.isAfter(Instant.now());
  }

  public void revogar() {
    this.revogadoEm = Instant.now();
  }
}
