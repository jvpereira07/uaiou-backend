package com.uaiou.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

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

  private BigDecimal score;

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
}
