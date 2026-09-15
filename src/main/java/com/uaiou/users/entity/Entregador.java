package com.uaiou.users.entity;

import com.uaiou.users.PaymentMethod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "entregador")
public class Entregador {

  @Id
  @Column(name = "usuario_id")
  private UUID usuarioId;

  @OneToOne
  @MapsId
  @JoinColumn(name = "usuario_id")
  private Usuario usuario;

  private String cpf;

  @Column(name = "veiculo_tipo")
  private String veiculoTipo;

  @Column(name = "veiculo_placa")
  private String veiculoPlaca;

  private boolean disponivel;

  @Column(name = "disponivel_desde")
  private Instant disponivelDesde;

  private BigDecimal lat;

  // Campo "longitude" mapeado para a coluna "long" (V1__identidade.sql): o nome curto no banco vem
  // do
  // modelo de domínio, mas "long" como identificador Java seria palavra reservada.
  @Column(name = "long")
  private BigDecimal longitude;

  @Column(name = "localizacao_em")
  private Instant localizacaoEm;

  private BigDecimal accuracy;

  private BigDecimal score;

  @Column(name = "score_componentes")
  @JdbcTypeCode(SqlTypes.JSON)
  private String scoreComponentes;

  @Column(name = "score_calculado_em")
  private Instant scoreCalculadoEm;

  @Column(name = "entregas_realizadas")
  private int entregasRealizadas;

  // V23 — tabela própria: o entregador pode aceitar mais de uma forma.
  @ElementCollection
  @CollectionTable(
      name = "entregador_forma_pagamento",
      joinColumns = @JoinColumn(name = "usuario_id"))
  @Column(name = "forma_pagamento")
  private Set<PaymentMethod> formasPagamento = new HashSet<>();

  protected Entregador() {
    // exigido pela JPA
  }

  public Entregador(Usuario usuario, String cpf, String veiculoTipo, String veiculoPlaca) {
    this.usuario = usuario;
    this.cpf = cpf;
    this.veiculoTipo = veiculoTipo;
    this.veiculoPlaca = veiculoPlaca;
    this.disponivel = false;
    this.entregasRealizadas = 0;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getCpf() {
    return cpf;
  }

  public String getVeiculoTipo() {
    return veiculoTipo;
  }

  public String getVeiculoPlaca() {
    return veiculoPlaca;
  }

  public boolean isDisponivel() {
    return disponivel;
  }

  public Instant getDisponivelDesde() {
    return disponivelDesde;
  }

  public BigDecimal getLat() {
    return lat;
  }

  public BigDecimal getLongitude() {
    return longitude;
  }

  public Instant getLocalizacaoEm() {
    return localizacaoEm;
  }

  public BigDecimal getAccuracy() {
    return accuracy;
  }

  public BigDecimal getScore() {
    return score;
  }

  public String getScoreComponentes() {
    return scoreComponentes;
  }

  public Instant getScoreCalculadoEm() {
    return scoreCalculadoEm;
  }

  /**
   * RF-20.3/RF-20.4 — valor e componentes gravados JUNTOS: expor o número sem os componentes
   * violaria a RN-04.1. {@code null} é legítimo (RF-20.9: sem base), não um erro.
   */
  public void atualizarScore(BigDecimal valor, String componentesJson, Instant calculadoEm) {
    this.score = valor;
    this.scoreComponentes = componentesJson;
    this.scoreCalculadoEm = calculadoEm;
  }

  public int getEntregasRealizadas() {
    return entregasRealizadas;
  }

  /**
   * RF-10.4/RF-10.5 — a última posição sobrescreve; não há histórico de trajeto (decisão de
   * privacidade e retenção, T-10).
   */
  public void atualizarLocalizacao(BigDecimal lat, BigDecimal longitude, BigDecimal accuracy) {
    this.lat = lat;
    this.longitude = longitude;
    this.accuracy = accuracy;
    this.localizacaoEm = agora();
  }

  /**
   * RF-10.2 — "recente" é relativo ao limite de frescor configurado (RF-10.7). Sem posição nenhuma
   * também é "não recente": o entregador nunca reportou.
   */
  public boolean temPosicaoRecente(Duration limite, Instant agora) {
    return localizacaoEm != null && localizacaoEm.isAfter(agora.minus(limite));
  }

  /** RF-10.1/RF-10.10 — {@code disponivelDesde} é o {@code since} devolvido pela rota. */
  public void ficarDisponivel() {
    if (!this.disponivel) {
      this.disponivel = true;
      this.disponivelDesde = agora();
    }
  }

  /**
   * {@code timestamptz} do Postgres guarda microssegundos; {@code Instant.now()} traz
   * nanossegundos. Sem truncar, a resposta da primeira escrita anunciaria dígitos que nunca foram
   * persistidos, e a mesma marca lida de volta depois pareceria ter mudado sozinha.
   */
  private static Instant agora() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  /**
   * RF-10.3 — desligar significa "não me mande mais", não "abandonei a entrega atual": nada aqui
   * toca em pedido. O par (disponivel, disponivelDesde) volta a zero junto.
   */
  public void ficarIndisponivel() {
    this.disponivel = false;
    this.disponivelDesde = null;
  }

  /** Ordem estável (a do enum) — o JSON não muda de ordem entre leituras. */
  public List<PaymentMethod> getFormasPagamento() {
    return formasPagamento.stream().sorted().toList();
  }

  /**
   * Campo livre do perfil — não passa por moderação. Substitui o conjunto inteiro; vazio significa
   * "ainda não informou".
   */
  public void atualizarFormasPagamento(Collection<PaymentMethod> formas) {
    this.formasPagamento.clear();
    this.formasPagamento.addAll(formas);
  }

  /** RF-15.9/RF-17.4 — contador de entregas concluídas, por código ou contestável. */
  public void incrementarEntregasRealizadas() {
    this.entregasRealizadas++;
  }
}
