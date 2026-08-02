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

  private BigDecimal score;

  @Column(name = "entregas_realizadas")
  private int entregasRealizadas;

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

  public int getEntregasRealizadas() {
    return entregasRealizadas;
  }
}
