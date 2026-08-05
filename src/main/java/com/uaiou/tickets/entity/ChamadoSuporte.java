package com.uaiou.tickets.entity;

import com.uaiou.tickets.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Chamado de suporte (V9__suporte.sql) — canal de mediação da plataforma, que na v1 absorve também
 * a contestação de entrega (T-17), já que não há disputa formal.
 *
 * <p>{@code referenciaTipo}/{@code referenciaId} são polimórficos de propósito (mesmo desenho de
 * {@code RegistroAuditoria}): apontam pedido, lançamento ou transação de crédito sem FK física,
 * para não acoplar o schema a cada novo tipo de referência.
 */
@Entity
@Table(name = "chamado_suporte")
public class ChamadoSuporte {

  @Id private UUID id;

  @Column(name = "autor_id")
  private UUID autorId;

  @Column(name = "admin_id")
  private UUID adminId;

  private String assunto;

  private TicketStatus status;

  @Column(name = "referencia_tipo")
  private String referenciaTipo;

  @Column(name = "referencia_id")
  private UUID referenciaId;

  @Column(name = "ajuste_tipo")
  private String ajusteTipo;

  @Column(name = "ajuste_id")
  private UUID ajusteId;

  @Column(name = "resolvido_em")
  private Instant resolvidoEm;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected ChamadoSuporte() {
    // exigido pela JPA
  }

  public ChamadoSuporte(
      UUID id, UUID autorId, String assunto, String referenciaTipo, UUID referenciaId) {
    this.id = id;
    this.autorId = autorId;
    this.assunto = assunto;
    this.referenciaTipo = referenciaTipo;
    this.referenciaId = referenciaId;
    this.status = TicketStatus.OPEN;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAutorId() {
    return autorId;
  }

  public UUID getAdminId() {
    return adminId;
  }

  public String getAssunto() {
    return assunto;
  }

  public TicketStatus getStatus() {
    return status;
  }

  public String getReferenciaTipo() {
    return referenciaTipo;
  }

  public UUID getReferenciaId() {
    return referenciaId;
  }

  public String getAjusteTipo() {
    return ajusteTipo;
  }

  public UUID getAjusteId() {
    return ajusteId;
  }

  public Instant getResolvidoEm() {
    return resolvidoEm;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public boolean pertenceA(UUID usuarioId) {
    return this.autorId.equals(usuarioId);
  }

  /** RF-21.5 — primeira resposta do admin move o chamado e o vincula como responsável. */
  public void assumirPeloAdmin(UUID adminId) {
    if (this.status == TicketStatus.OPEN) {
      this.status = TicketStatus.IN_PROGRESS;
    }
    if (this.adminId == null) {
      this.adminId = adminId;
    }
  }

  /** RF-21.6 — encerramento, com o par opcional do ajuste que resolveu o chamado (RN-13.2). */
  public void resolver(String ajusteTipo, UUID ajusteId) {
    this.status = TicketStatus.RESOLVED;
    this.resolvidoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
    this.ajusteTipo = ajusteTipo;
    this.ajusteId = ajusteId;
  }
}
