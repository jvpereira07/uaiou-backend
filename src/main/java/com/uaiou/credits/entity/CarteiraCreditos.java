package com.uaiou.credits.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Saldo-cache de créditos, sempre derivado de {@link TransacaoCredito} — nunca editado fora deste
 * par (V6__transacao_credito.sql, comentário). O {@code CHECK (saldo_creditos >= 0)} do banco é a
 * última linha de defesa (RF-09.11); {@link com.uaiou.credits.service.CreditWalletService} é quem
 * bloqueia a linha antes de debitar, pra concorrência não depender só do CHECK.
 */
@Entity
@Table(name = "carteira_creditos")
public class CarteiraCreditos {

  @Id
  @Column(name = "estabelecimento_id")
  private UUID estabelecimentoId;

  @Column(name = "saldo_creditos")
  private int saldoCreditos;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected CarteiraCreditos() {
    // exigido pela JPA
  }

  public CarteiraCreditos(UUID estabelecimentoId) {
    this.estabelecimentoId = estabelecimentoId;
    this.saldoCreditos = 0;
  }

  public UUID getEstabelecimentoId() {
    return estabelecimentoId;
  }

  public int getSaldoCreditos() {
    return saldoCreditos;
  }

  public void creditar(int quantidade) {
    if (quantidade <= 0) {
      throw new IllegalArgumentException("Quantidade a creditar precisa ser positiva.");
    }
    this.saldoCreditos += quantidade;
  }

  public void debitar(int quantidade) {
    if (quantidade <= 0) {
      throw new IllegalArgumentException("Quantidade a debitar precisa ser positiva.");
    }
    if (this.saldoCreditos < quantidade) {
      throw new IllegalStateException("Saldo insuficiente.");
    }
    this.saldoCreditos -= quantidade;
  }

  /** RF-09.10 — ajuste administrativo: positivo credita, negativo debita. */
  public void ajustar(int delta) {
    int novoSaldo = this.saldoCreditos + delta;
    if (novoSaldo < 0) {
      throw new IllegalStateException("Ajuste deixaria o saldo negativo.");
    }
    this.saldoCreditos = novoSaldo;
  }
}
