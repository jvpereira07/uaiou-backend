package com.uaiou.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * RF-08.9 — preferência por CANAL (V17). Ausência de linha significa "tudo ligado": o padrão é
 * receber, e a linha só passa a existir quando o usuário desliga alguma coisa.
 *
 * <p>Note que não há campo para "eventos críticos": {@code mandatory} é propriedade do tipo de
 * evento (ver {@code NotificationType}), não do usuário. Guardar aqui um booleano que ele nunca
 * pode desligar só criaria um estado capaz de mentir.
 */
@Entity
@Table(name = "preferencia_notificacao")
public class PreferenciaNotificacao {

  @Id
  @Column(name = "usuario_id")
  private UUID usuarioId;

  private boolean push;

  private boolean email;

  private boolean sms;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected PreferenciaNotificacao() {
    // exigido pela JPA
  }

  public PreferenciaNotificacao(UUID usuarioId) {
    this.usuarioId = usuarioId;
    this.push = true;
    this.email = true;
    this.sms = true;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public boolean isPush() {
    return push;
  }

  public boolean isEmail() {
    return email;
  }

  public boolean isSms() {
    return sms;
  }

  /** Aplica só o que veio — canal ausente no corpo permanece como estava. */
  public void atualizar(Boolean push, Boolean email, Boolean sms) {
    if (push != null) {
      this.push = push;
    }
    if (email != null) {
      this.email = email;
    }
    if (sms != null) {
      this.sms = sms;
    }
  }
}
