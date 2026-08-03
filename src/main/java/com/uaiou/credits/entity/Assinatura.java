package com.uaiou.credits.entity;

import com.uaiou.credits.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * RF-09.2 — no máximo uma assinatura {@code ativa} por estabelecimento (índice único parcial,
 * V4__creditos_plano.sql); histórico de assinaturas antigas não é apagado, só deixa de estar ativa.
 * Nenhuma rota de cancelamento existe em T-09 (escopo-v1.md corta self-service) — {@code status} só
 * nasce {@link SubscriptionStatus#ACTIVE} aqui.
 */
@Entity
@Table(name = "assinatura")
public class Assinatura {

  @Id private UUID id;

  @Column(name = "estabelecimento_id")
  private UUID estabelecimentoId;

  @Column(name = "plano_id")
  private UUID planoId;

  private SubscriptionStatus status;

  private LocalDate inicio;

  @Column(name = "proxima_renovacao")
  private LocalDate proximaRenovacao;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected Assinatura() {
    // exigido pela JPA
  }

  public Assinatura(UUID id, UUID estabelecimentoId, UUID planoId) {
    this.id = id;
    this.estabelecimentoId = estabelecimentoId;
    this.planoId = planoId;
    this.status = SubscriptionStatus.ACTIVE;
    this.inicio = LocalDate.now();
    this.proximaRenovacao = this.inicio.plusMonths(1);
  }

  public UUID getId() {
    return id;
  }

  public UUID getEstabelecimentoId() {
    return estabelecimentoId;
  }

  public UUID getPlanoId() {
    return planoId;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public LocalDate getInicio() {
    return inicio;
  }

  public LocalDate getProximaRenovacao() {
    return proximaRenovacao;
  }

  /** RF-09.2/RF-09.3 — troca de plano no meio do ciclo: o ciclo (início/renovação) não muda. */
  public void trocarPlano(UUID novoPlanoId) {
    this.planoId = novoPlanoId;
  }

  /** RF-09.4 — avança a partir da renovação anterior, não de "agora", para não acumular atraso. */
  public void avancarCiclo() {
    this.proximaRenovacao = this.proximaRenovacao.plusMonths(1);
  }
}
