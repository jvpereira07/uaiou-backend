package com.uaiou.orders.limits;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Uma linha por {@link BehaviorLimitRule} (V27) — as linhas nascem na migration, nunca aqui. */
@Entity
@Table(name = "limite_comportamento")
public class LimiteComportamento {

  @Id private String chave;

  private int maximo;

  @Column(name = "janela_minutos")
  private int janelaMinutos;

  @Column(name = "bloqueio_minutos")
  private int bloqueioMinutos;

  private boolean ativo;

  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  @Column(name = "atualizado_por")
  private UUID atualizadoPor;

  protected LimiteComportamento() {
    // exigido pela JPA
  }

  public BehaviorLimitRule getRegra() {
    return BehaviorLimitRule.fromDbKey(chave);
  }

  public int getMaximo() {
    return maximo;
  }

  public int getJanelaMinutos() {
    return janelaMinutos;
  }

  public Duration getJanela() {
    return Duration.ofMinutes(janelaMinutos);
  }

  public int getBloqueioMinutos() {
    return bloqueioMinutos;
  }

  public Duration getBloqueio() {
    return Duration.ofMinutes(bloqueioMinutos);
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

  public void atualizar(
      UUID adminId, int maximo, int janelaMinutos, int bloqueioMinutos, boolean ativo) {
    this.maximo = maximo;
    this.janelaMinutos = janelaMinutos;
    this.bloqueioMinutos = bloqueioMinutos;
    this.ativo = ativo;
    this.atualizadoPor = adminId;
    this.atualizadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }
}
