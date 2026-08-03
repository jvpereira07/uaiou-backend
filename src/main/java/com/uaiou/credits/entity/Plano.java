package com.uaiou.credits.entity;

import com.uaiou.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** RF-09.1 — catálogo de planos (V4__creditos_plano.sql). {@code preco} é só informativo na v1. */
@Entity
@Table(name = "plano")
public class Plano {

  @Id private UUID id;

  private String nome;

  @Column(name = "cota_mensal_creditos")
  private int cotaMensalCreditos;

  private Money preco;

  private boolean ativo;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected Plano() {
    // exigido pela JPA
  }

  public Plano(UUID id, String nome, int cotaMensalCreditos, Money preco) {
    this.id = id;
    this.nome = nome;
    this.cotaMensalCreditos = cotaMensalCreditos;
    this.preco = preco;
    this.ativo = true;
  }

  public UUID getId() {
    return id;
  }

  public String getNome() {
    return nome;
  }

  public int getCotaMensalCreditos() {
    return cotaMensalCreditos;
  }

  public Money getPreco() {
    return preco;
  }

  public boolean isAtivo() {
    return ativo;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  /** RF-09.1 — CRUD mínimo: só aplica os campos enviados. */
  public void atualizar(String nome, Integer cotaMensalCreditos, Money preco, Boolean ativo) {
    if (nome != null) {
      this.nome = nome;
    }
    if (cotaMensalCreditos != null) {
      this.cotaMensalCreditos = cotaMensalCreditos;
    }
    if (preco != null) {
      this.preco = preco;
    }
    if (ativo != null) {
      this.ativo = ativo;
    }
  }
}
