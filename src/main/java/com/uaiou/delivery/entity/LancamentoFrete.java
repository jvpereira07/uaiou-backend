package com.uaiou.delivery.entity;

import com.uaiou.delivery.LedgerEntryType;
import com.uaiou.delivery.LedgerStatus;
import java.math.BigDecimal;
import com.uaiou.shared.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Livro-razão da v1 — um lançamento por entrega, lido pelos dois lados: "a receber" para o
 * entregador, "a pagar" para o estabelecimento (V8__livro_razao_avaliacao.sql). A v1 registra o
 * valor devido; não o movimenta (sem saldo, sem débito automático) — mesmo lançamento nasce igual
 * na finalização por código (RF-15.9) e na contestável (RF-17.5): não há reserva a distinguir as
 * duas.
 *
 * <p>T-15/T-17 só criam a linha. Confirmação de acerto pelo entregador (RF-18) e leitura/extrato
 * são de T-18 — mesmo padrão de {@code Contraoferta}, que T-13 criou e T-14 completou.
 */
@Entity
@Table(name = "lancamento_frete")
public class LancamentoFrete {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "entregador_id")
  private UUID entregadorId;

  @Column(name = "estabelecimento_id")
  private UUID estabelecimentoId;

  private Money valor;

  private LedgerStatus status;

  private LedgerEntryType tipo;

  /** RF-26.18 — percentual aplicado na taxa de cancelamento; nulo no lançamento de frete. */
  @Column(name = "taxa_percentual")
  private BigDecimal taxaPercentual;

  @Column(name = "acertado_em")
  private Instant acertadoEm;

  @Column(name = "acertado_por")
  private UUID acertadoPor;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected LancamentoFrete() {
    // exigido pela JPA
  }

  public LancamentoFrete(
      UUID id, UUID pedidoId, UUID entregadorId, UUID estabelecimentoId, Money valor) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.entregadorId = entregadorId;
    this.estabelecimentoId = estabelecimentoId;
    this.valor = valor;
    this.status = LedgerStatus.RECEIVABLE;
    this.tipo = LedgerEntryType.DELIVERY_FEE;
  }

  /**
   * RF-26.17 — taxa de cancelamento após a chegada do entregador. Mesmo livro, mesma leitura dos
   * dois lados (RF-18.2); o percentual fica gravado para que mudar a configuração não reescreva o
   * passado (RF-26.18).
   */
  public static LancamentoFrete taxaDeCancelamento(
      UUID id,
      UUID pedidoId,
      UUID entregadorId,
      UUID estabelecimentoId,
      Money valor,
      BigDecimal taxaPercentual) {
    LancamentoFrete lancamento =
        new LancamentoFrete(id, pedidoId, entregadorId, estabelecimentoId, valor);
    lancamento.tipo = LedgerEntryType.CANCELLATION_FEE;
    lancamento.taxaPercentual = taxaPercentual;
    return lancamento;
  }

  public LedgerEntryType getTipo() {
    return tipo;
  }

  public BigDecimal getTaxaPercentual() {
    return taxaPercentual;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public UUID getEntregadorId() {
    return entregadorId;
  }

  public UUID getEstabelecimentoId() {
    return estabelecimentoId;
  }

  public Money getValor() {
    return valor;
  }

  public LedgerStatus getStatus() {
    return status;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  public Instant getAcertadoEm() {
    return acertadoEm;
  }

  public UUID getAcertadoPor() {
    return acertadoPor;
  }

  public boolean pertenceAoEntregador(UUID entregadorId) {
    return this.entregadorId.equals(entregadorId);
  }

  public boolean pertenceAoEstabelecimento(UUID estabelecimentoId) {
    return this.estabelecimentoId.equals(estabelecimentoId);
  }

  /**
   * RF-18.4/RF-18.5 — só o ENTREGADOR confirma (escopo-v1.md: é quem sabe se o dinheiro entrou e é
   * a parte mais exposta). Idempotência é responsabilidade do chamador: repetir sobre um já {@code
   * acertado} deve virar 409, não um no-op silencioso — por isso este mutador não se protege
   * sozinho contra o estado atual.
   */
  public void acertar(UUID acertadoPor) {
    this.status = LedgerStatus.SETTLED;
    this.acertadoEm = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    this.acertadoPor = acertadoPor;
  }
}
