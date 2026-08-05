package com.uaiou.delivery.entity;

import com.uaiou.delivery.DeliveryCodeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Código de entrega — 1:1 com o pedido (V7__validacao_entrega.sql).
 *
 * <p>Duas representações do mesmo código, de propósito (RN-08.3): {@code codigoHash} serve à
 * comparação na finalização e não permite recuperar o valor; {@code codigoCifrado} permite a
 * leitura auditada pelo estabelecimento, com a chave vivendo <strong>fora</strong> do banco. O
 * valor claro nunca é persistido.
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

  private DeliveryCodeStatus status;

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
    this.status = DeliveryCodeStatus.ISSUED;
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

  public int getTentativas() {
    return tentativas;
  }

  public DeliveryCodeStatus getStatus() {
    return status;
  }

  public Instant getExpiraEm() {
    return expiraEm;
  }

  public int getLeituras() {
    return leituras;
  }

  public Instant getUltimaLeituraEm() {
    return ultimaLeituraEm;
  }

  public boolean estaExpirado(Instant agora) {
    return status == DeliveryCodeStatus.ISSUED && expiraEm.isBefore(agora);
  }

  /**
   * RF-15.8 — escrita <strong>fora</strong> da transação principal do chamador (o serviço decide
   * isso commitando esta chamada isoladamente): tentativa errada precisa persistir mesmo que a
   * resposta HTTP seguinte falhe por outro motivo, senão o contador de força bruta reseta sozinho.
   *
   * @return o número de tentativas após o incremento.
   */
  public int registrarTentativaErrada() {
    this.tentativas++;
    return this.tentativas;
  }

  /** RF-15.8 — N-ésima falha: bloqueia o código e a resposta passa a apontar a contingência. */
  public void bloquear() {
    this.status = DeliveryCodeStatus.BLOCKED;
  }

  /** RF-15.9 — sucesso: o código não serve mais para nova tentativa. */
  public void validar() {
    this.status = DeliveryCodeStatus.VALIDATED;
    this.validadoEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  public void expirar() {
    this.status = DeliveryCodeStatus.EXPIRED;
  }

  /**
   * RF-15.11 — GET que escreve auditoria: contrapartida de expor o código ao estabelecimento.
   * Sequência "estabelecimento leu → entregador acertou de primeira depois de N erros" é padrão de
   * conluio detectável só porque esta marca existe.
   */
  public void registrarLeitura() {
    this.leituras++;
    this.ultimaLeituraEm = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }
}
