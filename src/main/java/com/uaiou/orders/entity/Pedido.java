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

  @Column(name = "contestavel_liberado")
  private boolean contestavelLiberado;

  @Column(name = "chegou_em")
  private Instant chegouEm;

  @Column(name = "leituras_no_raio_coleta")
  private short leiturasNoRaioColeta;

  @Column(name = "coletado_em")
  private Instant coletadoEm;

  @Column(name = "lembrete_coleta_em")
  private Instant lembreteColetaEm;

  @Column(name = "cancelado_em")
  private Instant canceladoEm;

  @Column(name = "cancelamento_motivo")
  private String cancelamentoMotivo;

  @Column(name = "cancelamento_nota")
  private String cancelamentoNota;

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

  /**
   * RF-13.4 — aceite: a atribuição e o valor final nascem juntos (ck_pedido_atribuicao_coerente,
   * V5). O frete final é o proposto; contraoferta aceita por outro valor é caminho de T-14.
   */
  public void aceitarPor(java.util.UUID entregadorId) {
    this.entregadorId = entregadorId;
    this.freteFinal = this.freteProposto;
    this.status = OrderStatus.ACCEPTED;
    this.aceitoEm = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  public Instant getAceitoEm() {
    return aceitoEm;
  }

  /**
   * RF-14.6 — aceitar contraoferta atribui pelo <strong>valor proposto</strong>, não pelo frete
   * original: é essa diferença de valor final que distingue este caminho de {@link
   * #aceitarPor(UUID)}.
   */
  public void aceitarPorContraoferta(UUID entregadorId, Money valorProposto) {
    this.entregadorId = entregadorId;
    this.freteFinal = valorProposto;
    this.status = OrderStatus.ACCEPTED;
    this.aceitoEm = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  /** RF-11.3 — último passo da transação de criação, depois do crédito já debitado. */
  public void publicar() {
    this.status = OrderStatus.PUBLISHED;
  }

  /** RF-14.1 — negociar não reserva (RF-14.3): o pedido segue visível e aceitável por outros. */
  public void iniciarNegociacao() {
    if (this.status == OrderStatus.PUBLISHED) {
      this.status = OrderStatus.IN_NEGOTIATION;
    }
  }

  /** RF-14.7 — recusa sem outra pendente devolve o pedido ao estado de "aceitável direto". */
  public void voltarAPublicado() {
    if (this.status == OrderStatus.IN_NEGOTIATION) {
      this.status = OrderStatus.PUBLISHED;
    }
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

  public Instant getFinalizadoEm() {
    return finalizadoEm;
  }

  public boolean isContestavelLiberado() {
    return contestavelLiberado;
  }

  /** RF-15.9 — finalização por código: prova forte, sem intermediário. */
  public void finalizar() {
    this.status = OrderStatus.FINALIZED;
    this.finalizadoEm = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  /**
   * RF-17.4 — finalização com evidência reforçada (foto), quando o código não pôde ser validado.
   */
  public void finalizarContestavel() {
    this.status = OrderStatus.CONTESTABLE_FINALIZED;
    this.finalizadoEm = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  /**
   * RF-17.6 — job de consolidação da janela: sem contestação no prazo, o pedido vira o mesmo estado
   * final de uma entrega por código. Não mexe em {@code finalizadoEm}: a entrega já aconteceu
   * naquele instante, a consolidação só resolve a pendência administrativa.
   */
  public void consolidarContestavelEmFinalizado() {
    this.status = OrderStatus.FINALIZED;
  }

  /**
   * RF-16.5/RF-17.1 — escrito pelo SERVIDOR (a escada de contingência, T-16), nunca pelo cliente: é
   * o que impede o entregador de pular a validação por conveniência (RN-10.1).
   */
  public void liberarContestavel() {
    this.contestavelLiberado = true;
  }

  public Instant getChegouEm() {
    return chegouEm;
  }

  public Instant getColetadoEm() {
    return coletadoEm;
  }

  public Instant getLembreteColetaEm() {
    return lembreteColetaEm;
  }

  public Instant getCanceladoEm() {
    return canceladoEm;
  }

  public String getCancelamentoMotivo() {
    return cancelamentoMotivo;
  }

  public String getCancelamentoNota() {
    return cancelamentoNota;
  }

  /** T-26 — aceito e ainda sem pacote: a janela em que chegar, coletar, cancelar e desistir valem. */
  public boolean aguardandoColeta() {
    return status == OrderStatus.ACCEPTED;
  }

  /** RF-26.14/D3 — o estabelecimento cancela até a coleta. */
  public boolean podeSerCancelado() {
    return status == OrderStatus.PUBLISHED
        || status == OrderStatus.IN_NEGOTIATION
        || status == OrderStatus.ACCEPTED;
  }

  /**
   * RF-26.1/RF-26.2/RF-26.4 — uma leitura de posição do entregador atribuído. Fora do raio zera a
   * sequência (histerese contra salto de GPS); a N-ésima leitura consecutiva dentro grava a chegada
   * uma única vez.
   *
   * @return {@code true} só na leitura que registrou a chegada.
   */
  public boolean registrarLeituraNoRaioColeta(boolean dentroDoRaio, int leiturasNecessarias) {
    if (!aguardandoColeta() || chegouEm != null) {
      return false;
    }
    if (!dentroDoRaio) {
      this.leiturasNoRaioColeta = 0;
      return false;
    }
    this.leiturasNoRaioColeta++;
    if (this.leiturasNoRaioColeta < leiturasNecessarias) {
      return false;
    }
    this.chegouEm = agora();
    return true;
  }

  /**
   * RF-26.3/RF-26.4 — chegada declarada pelo botão "Cheguei", já validada pelo chamador.
   *
   * @return {@code true} se esta chamada registrou a chegada; {@code false} se ela já existia.
   */
  public boolean registrarChegada() {
    if (chegouEm != null) {
      return false;
    }
    this.chegouEm = agora();
    return true;
  }

  /** RF-26.7 — o estabelecimento entregou o pacote na mão do entregador. */
  public void confirmarColeta() {
    this.status = OrderStatus.PICKED_UP;
    this.coletadoEm = agora();
  }

  /** RF-26.10 — marca do último reaviso pedido pelo entregador. */
  public void registrarLembreteColeta() {
    this.lembreteColetaEm = agora();
  }

  /** RF-26.14 — estado terminal; os marcos do aceite ficam, para a auditoria e a taxa. */
  public void cancelar(String motivo, String nota) {
    this.status = OrderStatus.CANCELLED;
    this.canceladoEm = agora();
    this.cancelamentoMotivo = motivo;
    this.cancelamentoNota = nota;
  }

  /**
   * RF-26.23 — desistir não é cancelar: o pedido volta a ser oferecido como se ninguém o tivesse
   * aceitado. O frete final some junto com a atribuição (ck_pedido_atribuicao_coerente), e o valor
   * de contraoferta aceita morre com ele — a vitrine volta a mostrar o frete proposto.
   */
  public void desfazerAceite() {
    this.entregadorId = null;
    this.freteFinal = null;
    this.aceitoEm = null;
    this.chegouEm = null;
    this.leiturasNoRaioColeta = 0;
    this.lembreteColetaEm = null;
    this.contestavelLiberado = false;
    this.status = OrderStatus.PUBLISHED;
  }

  private static Instant agora() {
    return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  /** RF-16.4 — o estabelecimento corrige um pedido que nasceu sem telefone do recebedor. */
  public void atualizarTelefoneRecebedor(String telefone) {
    this.recebedorTelefone = telefone;
  }
}
