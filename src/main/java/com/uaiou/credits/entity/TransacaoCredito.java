package com.uaiou.credits.entity;

import com.uaiou.credits.CreditTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Extrato de créditos, append-only por natureza (nunca há update aqui — o saldo em {@link
 * CarteiraCreditos} é sempre derivado, mas o lançamento em si nunca muda,
 * V6__transacao_credito.sql). {@code quantidade} é positiva para {@code cota_mensal}/{@code ajuste}
 * de crédito, negativa para {@code consumo_postagem}/{@code ajuste} de débito — nunca zero (CHECK
 * do banco).
 */
@Entity
@Table(name = "transacao_credito")
public class TransacaoCredito {

  @Id private UUID id;

  @Column(name = "estabelecimento_id")
  private UUID estabelecimentoId;

  private CreditTransactionType tipo;

  private int quantidade;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "assinatura_id")
  private UUID assinaturaId;

  @Column(name = "registro_auditoria_id")
  private UUID registroAuditoriaId;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected TransacaoCredito() {
    // exigido pela JPA
  }

  public TransacaoCredito(
      UUID id,
      UUID estabelecimentoId,
      CreditTransactionType tipo,
      int quantidade,
      UUID pedidoId,
      UUID assinaturaId) {
    this.id = id;
    this.estabelecimentoId = estabelecimentoId;
    this.tipo = tipo;
    this.quantidade = quantidade;
    this.pedidoId = pedidoId;
    this.assinaturaId = assinaturaId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEstabelecimentoId() {
    return estabelecimentoId;
  }

  public CreditTransactionType getTipo() {
    return tipo;
  }

  public int getQuantidade() {
    return quantidade;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public UUID getAssinaturaId() {
    return assinaturaId;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
