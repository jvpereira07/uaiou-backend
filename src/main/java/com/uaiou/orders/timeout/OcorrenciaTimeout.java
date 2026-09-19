package com.uaiou.orders.timeout;

import com.uaiou.orders.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Um disparo de timeout sobre um pedido (V26) — append-only, é a "assinatura" do job. */
@Entity
@Table(name = "ocorrencia_timeout")
public class OcorrenciaTimeout {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  private String chave;

  private String acao;

  @Column(name = "status_anterior")
  private OrderStatus statusAnterior;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected OcorrenciaTimeout() {
    // exigido pela JPA
  }

  public OcorrenciaTimeout(
      UUID id, UUID pedidoId, OrderTimeoutRule regra, OrderStatus statusAnterior) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.chave = regra.dbKey();
    this.acao = regra.action().dbValue();
    this.statusAnterior = statusAnterior;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public OrderTimeoutRule getRegra() {
    return OrderTimeoutRule.fromDbKey(chave);
  }

  public OrderTimeoutRule.TimeoutAction getAcao() {
    return OrderTimeoutRule.TimeoutAction.fromDbValue(acao);
  }

  public OrderStatus getStatusAnterior() {
    return statusAnterior;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
