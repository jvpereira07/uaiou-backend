package com.uaiou.notifications.entity;

import com.uaiou.notifications.NotificationPriority;
import com.uaiou.notifications.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * RF-08.2 — a linha no inbox é a <strong>fonte de verdade</strong>; o push é tentativa adicional.
 * Push perdido (aparelho offline, permissão negada, token expirado) não pode perder o evento.
 *
 * <p>{@code tipo} é gravado pelo nome do contrato ({@code order.published}), não pelo {@code
 * name()} do enum: é o mesmo texto que o cliente vê e que o índice de consulta usa.
 */
@Entity
@Table(name = "notificacao")
public class Notificacao {

  @Id private UUID id;

  @Column(name = "usuario_id")
  private UUID usuarioId;

  private String tipo;

  private NotificationPriority prioridade;

  private String titulo;

  private String corpo;

  @JdbcTypeCode(SqlTypes.JSON)
  private String payload;

  @Column(name = "lida_em")
  private Instant lidaEm;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected Notificacao() {
    // exigido pela JPA
  }

  public Notificacao(
      UUID id, UUID usuarioId, NotificationType tipo, String titulo, String corpo, String payload) {
    this.id = id;
    this.usuarioId = usuarioId;
    this.tipo = tipo.contractName();
    this.prioridade = tipo.priority();
    this.titulo = titulo;
    this.corpo = corpo;
    this.payload = payload;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getTipo() {
    return tipo;
  }

  public NotificationPriority getPrioridade() {
    return prioridade;
  }

  public String getTitulo() {
    return titulo;
  }

  public String getCorpo() {
    return corpo;
  }

  public String getPayload() {
    return payload;
  }

  public Instant getLidaEm() {
    return lidaEm;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  /** Idempotente: marcar como lida duas vezes não move o carimbo (RF-08.5). */
  public void marcarLida() {
    if (this.lidaEm == null) {
      this.lidaEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
  }
}
