package com.uaiou.orders.entity;

import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Agregado central do ciclo de vida (V5__pedido_negociacao.sql). T-11 cobre só o nascimento —
 * criação e publicação; aceite (T-13), contraoferta (T-14) e finalização (T-15/T-17) escrevem os
 * campos restantes.
 *
 * <p>{@code numero} é único POR ESTABELECIMENTO, não globalmente: cada loja tem a própria numeração
 * (🖼 "Pedido nº 0011").
 */
@Entity
@Table(name = "pedido")
public class Pedido {

  @Id private UUID id;

  private String numero;

  @Column(name = "estabelecimento_id")
  private UUID estabelecimentoId;

  @Column(name = "entregador_id")
  private UUID entregadorId;

  private OrderStatus status;

  @Column(name = "frete_proposto")
  private Money freteProposto;

  @Column(name = "frete_final")
  private Money freteFinal;

  @Column(name = "creditos_consumidos")
  private int creditosConsumidos;

  @Column(name = "dest_bairro")
  private String destBairro;

  @Column(name = "dest_rua")
  private String destRua;

  @Column(name = "dest_numero")
  private String destNumero;

  @Column(name = "dest_complemento")
  private String destComplemento;

  @Column(name = "dest_lat")
  private BigDecimal destLat;

  @Column(name = "dest_long")
  private BigDecimal destLong;

  @Column(name = "recebedor_nome")
  private String recebedorNome;

  @Column(name = "recebedor_telefone")
  private String recebedorTelefone;

  @Column(name = "hora_prevista_entrega")
  private Instant horaPrevistaEntrega;

  @Column(name = "aceito_em")
  private Instant aceitoEm;

  @Column(name = "finalizado_em")
  private Instant finalizadoEm;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected Pedido() {
    // exigido pela JPA
  }

  public Pedido(
      UUID id,
      String numero,
      UUID estabelecimentoId,
      Money freteProposto,
      int creditosConsumidos,
      Destino destino,
      Recebedor recebedor,
      Instant horaPrevistaEntrega) {
    this.id = id;
    this.numero = numero;
    this.estabelecimentoId = estabelecimentoId;
    this.freteProposto = freteProposto;
    this.creditosConsumidos = creditosConsumidos;
    this.destBairro = destino.bairro();
    this.destRua = destino.rua();
    this.destNumero = destino.numero();
    this.destComplemento = destino.complemento();
    this.destLat = destino.lat();
    this.destLong = destino.longitude();
    this.recebedorNome = recebedor.nome();
    this.recebedorTelefone = recebedor.telefone();
    this.horaPrevistaEntrega = horaPrevistaEntrega;
    // RF-11.3: nasce "criado" e só vira "publicado" ao fim da transação, depois do crédito
    // debitado.
    this.status = OrderStatus.CREATED;
  }

  /**
   * Parte do endereço que o pedido guarda — sem cidade, que V5 não modelou (v1 é de uma praça só).
   */
  public record Destino(
      String bairro,
      String rua,
      String numero,
      String complemento,
      BigDecimal lat,
      BigDecimal longitude) {}

  public record Recebedor(String nome, String telefone) {}

  public UUID getId() {
    return id;
  }

  public String getNumero() {
    return numero;
  }

  public UUID getEstabelecimentoId() {
    return estabelecimentoId;
  }

  public UUID getEntregadorId() {
    return entregadorId;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public Money getFreteProposto() {
    return freteProposto;
  }

  public Money getFreteFinal() {
    return freteFinal;
  }

  public int getCreditosConsumidos() {
    return creditosConsumidos;
  }

  public String getDestBairro() {
    return destBairro;
  }

  public String getDestRua() {
    return destRua;
  }

  public String getDestNumero() {
    return destNumero;
  }

  public String getDestComplemento() {
    return destComplemento;
  }

  public BigDecimal getDestLat() {
    return destLat;
  }

  public BigDecimal getDestLong() {
    return destLong;
  }

  public String getRecebedorNome() {
    return recebedorNome;
  }

  public String getRecebedorTelefone() {
    return recebedorTelefone;
  }

  public Instant getHoraPrevistaEntrega() {
    return horaPrevistaEntrega;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  /** RF-11.3 — último passo da transação de criação, depois do crédito já debitado. */
  public void publicar() {
    this.status = OrderStatus.PUBLISHED;
  }

  /** Visível ao entregador elegível na vitrine (RF-11.6): publicado ou já em negociação. */
  public boolean estaNaVitrine() {
    return status == OrderStatus.PUBLISHED || status == OrderStatus.IN_NEGOTIATION;
  }

  public boolean pertenceAoEstabelecimento(UUID estabelecimentoId) {
    return this.estabelecimentoId.equals(estabelecimentoId);
  }

  public boolean estaAtribuidoA(UUID entregadorId) {
    return this.entregadorId != null && this.entregadorId.equals(entregadorId);
  }
}
