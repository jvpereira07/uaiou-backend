package com.uaiou.orders.timeout;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Uma linha por {@link OrderTimeoutRule} (V26) — as linhas nascem na migration, nunca aqui. */
@Entity
@Table(name = "configuracao_timeout")
public class ConfiguracaoTimeout {

  @Id private String chave;

  @Column(name = "duracao_minutos")
  private int duracaoMinutos;

  private boolean ativo;

  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  @Column(name = "atualizado_por")
  private UUID atualizadoPor;

  protected ConfiguracaoTimeout() {
    // exigido pela JPA
  }

  public OrderTimeoutRule getRegra() {
    return OrderTimeoutRule.fromDbKey(chave);
  }

  public int getDuracaoMinutos() {
    return duracaoMinutos;
  }

  public Duration getDuracao() {
    return Duration.ofMinutes(duracaoMinutos);
  }

  public boolean isAtivo() {
    return ativo;
  }

  public Instant getAtualizadoEm() {
    return atualizadoEm;
  }

  public UUID getAtualizadoPor() {
    return atualizadoPor;
  }

  public void atualizar(UUID adminId, int duracaoMinutos, boolean ativo) {
    this.duracaoMinutos = duracaoMinutos;
    this.ativo = ativo;
    this.atualizadoPor = adminId;
    this.atualizadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }
}
