package com.uaiou.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "estabelecimento")
public class Estabelecimento {

  @Id
  @Column(name = "usuario_id")
  private UUID usuarioId;

  @OneToOne
  @MapsId
  @JoinColumn(name = "usuario_id")
  private Usuario usuario;

  private String cnpj;

  @Column(name = "nome_fantasia")
  private String nomeFantasia;

  @Column(name = "logo_object_key")
  private String logoObjectKey;

  private String bairro;

  private String rua;

  private String numero;

  private String cidade;

  private String cep;

  private BigDecimal score;

  @Column(name = "score_componentes")
  @JdbcTypeCode(SqlTypes.JSON)
  private String scoreComponentes;

  @Column(name = "score_calculado_em")
  private Instant scoreCalculadoEm;

  protected Estabelecimento() {
    // exigido pela JPA
  }

  public Estabelecimento(Usuario usuario, String cnpj, String nomeFantasia) {
    this.usuario = usuario;
    this.cnpj = cnpj;
    this.nomeFantasia = nomeFantasia;
  }

  public UUID getUsuarioId() {
    return usuarioId;
  }

  public String getCnpj() {
    return cnpj;
  }

  public String getNomeFantasia() {
    return nomeFantasia;
  }

  public String getLogoObjectKey() {
    return logoObjectKey;
  }

  public String getBairro() {
    return bairro;
  }

  public String getRua() {
    return rua;
  }

  public String getNumero() {
    return numero;
  }

  public String getCidade() {
    return cidade;
  }

  public String getCep() {
    return cep;
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
   * RF-20.3/RF-20.4 — valor e componentes são gravados JUNTOS, na mesma escrita: expor o número sem
   * os componentes violaria a RN-04.1. {@code null} é legítimo (RF-20.9: sem base), não um erro.
   */
  public void atualizarScore(BigDecimal valor, String componentesJson, Instant calculadoEm) {
    this.score = valor;
    this.scoreComponentes = componentesJson;
    this.scoreCalculadoEm = calculadoEm;
  }

  /** Campo livre (RF-04.3, T-04) — aplica direto, sem passar por moderação. */
  public void atualizarLogo(String logoObjectKey) {
    this.logoObjectKey = logoObjectKey;
  }

  /**
   * Campo livre (RF-04.3, T-04) — aplica direto, sem passar por moderação. Cada parte do endereço
   * só é sobrescrita se vier não-nula: PATCH /me manda o grupo inteiro por conveniência do cliente,
   * mas manter a semântica "só aplica o que veio" por campo evita que editar só o CEP apague
   * bairro/rua/número/cidade.
   */
  public void atualizarEndereco(
      String bairro, String rua, String numero, String cidade, String cep) {
    if (bairro != null) {
      this.bairro = bairro;
    }
    if (rua != null) {
      this.rua = rua;
    }
    if (numero != null) {
      this.numero = numero;
    }
    if (cidade != null) {
      this.cidade = cidade;
    }
    if (cep != null) {
      this.cep = cep;
    }
  }
}
