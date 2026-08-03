package com.uaiou.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Código de entrega — 1:1 com o pedido (V7__validacao_entrega.sql).
 *
 * <p>Duas representações do mesmo código, de propósito (RN-08.3): {@code codigoHash} serve à
 * comparação na finalização (T-15) e não permite recuperar o valor; {@code codigoCifrado} permite a
 * leitura auditada pelo estabelecimento, com a chave vivendo <strong>fora</strong> do banco. O
 * valor claro nunca é persistido.
 *
 * <p>T-13 só cria a linha (RF-13.4, critério de aceite 8). Validação, contagem de tentativas,
 * leitura auditada e expiração são de T-15.
 */
@Entity
@Table(name = "otp")
public class Otp {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "codigo_hash")
  private String codigoHash;

  @Column(name = "codigo_cifrado")
  private String codigoCifrado;

  private int tentativas;

  private String status;

  @Column(name = "expira_em")
  private Instant expiraEm;

  @Column(name = "validado_em")
  private Instant validadoEm;

  private int leituras;

  @Column(name = "ultima_leitura_em")
  private Instant ultimaLeituraEm;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected Otp() {
    // exigido pela JPA
  }

  public Otp(UUID id, UUID pedidoId, String codigoHash, String codigoCifrado, Instant expiraEm) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.codigoHash = codigoHash;
    this.codigoCifrado = codigoCifrado;
    this.expiraEm = expiraEm;
    this.status = "gerado";
    this.tentativas = 0;
    this.leituras = 0;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public String getCodigoHash() {
    return codigoHash;
  }

  public String getCodigoCifrado() {
    return codigoCifrado;
  }

  public String getStatus() {
    return status;
  }

  public Instant getExpiraEm() {
    return expiraEm;
  }

  public int getLeituras() {
    return leituras;
  }
}
