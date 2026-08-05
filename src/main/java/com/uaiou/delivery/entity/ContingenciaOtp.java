package com.uaiou.delivery.entity;

import com.uaiou.delivery.ContingencyChannel;
import com.uaiou.delivery.ContingencyResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Histórico da escada de contingência — uma linha por evento, NUNCA atualizada
 * (V7__validacao_entrega.sql). Base probatória para a atribuição de falha (RN-09.3) e para o dossiê
 * de suporte (RF-16.10/T-21). Sem mutadores de propósito, mesmo padrão de {@code RegistroAuditoria}
 * (T-07): histórico não se edita, só se acumula.
 */
@Entity
@Table(name = "contingencia_otp")
public class ContingenciaOtp {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  private short degrau;

  private ContingencyChannel canal;

  private ContingencyResult resultado;

  @Column(name = "prazo_em")
  private Instant prazoEm;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected ContingenciaOtp() {
    // exigido pela JPA
  }

  public ContingenciaOtp(
      UUID id,
      UUID pedidoId,
      int degrau,
      ContingencyChannel canal,
      ContingencyResult resultado,
      Instant prazoEm) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.degrau = (short) degrau;
    this.canal = canal;
    this.resultado = resultado;
    this.prazoEm = prazoEm;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public int getDegrau() {
    return degrau;
  }

  public ContingencyChannel getCanal() {
    return canal;
  }

  public ContingencyResult getResultado() {
    return resultado;
  }

  public Instant getPrazoEm() {
    return prazoEm;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
