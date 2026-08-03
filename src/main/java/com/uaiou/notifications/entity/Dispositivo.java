package com.uaiou.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * RF-08.3 — a UNIQUE é no {@code push_token} sozinho, não no par com usuário: o mesmo aparelho
 * reinstalado ou passado para outra conta não pode gerar duas linhas, senão a notificação de um
 * usuário chegaria no outro (cenário real de aparelho compartilhado entre turnos).
 */
@Entity
@Table(name = "dispositivo")
public class Dispositivo {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  private String plataforma;

  @Column(name = "push_token")
  private String pushToken;

  @Column(name = "app_version")
  private String appVersion;

  @Column(name = "ultimo_uso_em")
  private Instant ultimoUsoEm;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected Dispositivo() {
    // exigido pela JPA
  }

  public Dispositivo(
      UUID id, UUID usuarioId, String plataforma, String pushToken, String appVersion) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.plataforma = plataforma;
    this.pushToken = pushToken;
    this.appVersion = appVersion;
    this.ultimoUsoEm = agora();
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getPlataforma() {
    return plataforma;
  }

  public String getPushToken() {
    return pushToken;
  }

  public String getAppVersion() {
    return appVersion;
  }

  public Instant getUltimoUsoEm() {
    return ultimoUsoEm;
  }

  /** RF-08.3 — reassocia o token ao usuário atual em vez de criar uma segunda linha. */
  public void reassociar(UUID usuarioId, String plataforma, String appVersion) {
    this.usuarioId = usuarioId;
    this.plataforma = plataforma;
    this.appVersion = appVersion;
    this.ultimoUsoEm = agora();
  }

  private static Instant agora() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }
}
