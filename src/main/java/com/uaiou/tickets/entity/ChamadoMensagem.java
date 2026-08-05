package com.uaiou.tickets.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Thread do chamado — append-only, sem {@code atualizado_em} (V9__suporte.sql). */
@Entity
@Table(name = "chamado_mensagem")
public class ChamadoMensagem {

  @Id private UUID id;

  @Column(name = "chamado_id")
  private UUID chamadoId;

  @Column(name = "autor_id")
  private UUID autorId;

  private String mensagem;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected ChamadoMensagem() {
    // exigido pela JPA
  }

  public ChamadoMensagem(UUID id, UUID chamadoId, UUID autorId, String mensagem) {
    this.id = id;
    this.chamadoId = chamadoId;
    this.autorId = autorId;
    this.mensagem = mensagem;
  }

  public UUID getId() {
    return id;
  }

  public UUID getChamadoId() {
    return chamadoId;
  }

  public UUID getAutorId() {
    return autorId;
  }

  public String getMensagem() {
    return mensagem;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
