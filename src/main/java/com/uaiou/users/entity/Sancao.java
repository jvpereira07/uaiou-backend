package com.uaiou.users.entity;

import com.uaiou.users.SanctionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Suspensão (com prazo) ou banimento (sem prazo) — RN-11.3. A leitura (bloquear login/escrita) já
 * existia desde T-03 via {@link com.uaiou.auth.service.AccountStatusGuard}; T-07 adiciona a
 * escrita: aplicar e encerrar sanção.
 */
@Entity
@Table(name = "sancao")
public class Sancao {

  @Id private UUID id;

  @Column(name = "usuario_alvo_id")
  private UUID usuarioAlvoId;

  @Column(name = "admin_id")
  private UUID adminId;

  private SanctionType tipo;

  private String motivo;

  @CreationTimestamp private Instant inicio;

  private Instant fim;

  private boolean ativa;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected Sancao() {
    // exigido pela JPA
  }

  public Sancao(
      UUID id, UUID usuarioAlvoId, UUID adminId, SanctionType tipo, String motivo, Instant fim) {
    this.id = id;
    this.usuarioAlvoId = usuarioAlvoId;
    this.adminId = adminId;
    this.tipo = tipo;
    this.motivo = motivo;
    this.fim = fim;
    this.ativa = true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioAlvoId() {
    return usuarioAlvoId;
  }

  public UUID getAdminId() {
    return adminId;
  }

  public SanctionType getTipo() {
    return tipo;
  }

  public String getMotivo() {
    return motivo;
  }

  public Instant getInicio() {
    return inicio;
  }

  public Instant getFim() {
    return fim;
  }

  public boolean isAtiva() {
    return ativa;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public boolean isBanimento() {
    return tipo == SanctionType.BAN;
  }

  /**
   * RF-07.7: encerramento antecipado (admin, {@code DELETE /admin/sanctions/{id}}) ou automático
   * (job de expiração de suspensão) chamam o mesmo método — a diferença é só quem decidiu, não o
   * efeito.
   */
  public void encerrar() {
    this.ativa = false;
  }
}
