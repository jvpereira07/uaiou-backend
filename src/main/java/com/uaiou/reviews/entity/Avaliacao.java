package com.uaiou.reviews.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Avaliação mútua pós-entrega (V8__livro_razao_avaliacao.sql, RN-03.1). {@code ativa = false}
 * distingue a automática do job (RF-19.5) da opinião real — RF-19.6: só as ativas têm peso pleno no
 * score, senão o padrão positivo inflaria a média de todo mundo igualmente.
 *
 * <p>Sem mutador de nota/comentário: uma avaliação não se edita depois de criada. A UK (pedido_id,
 * autor_id) garante que o job do padrão positivo (RF-19.5) nunca sobrescreve uma avaliação real que
 * já exista — ele só cria a automática quando não há linha nenhuma para aquele par.
 */
@Entity
@Table(name = "avaliacao")
public class Avaliacao {

  @Id private UUID id;

  @Column(name = "pedido_id")
  private UUID pedidoId;

  @Column(name = "autor_id")
  private UUID autorId;

  @Column(name = "alvo_id")
  private UUID alvoId;

  private short nota;

  private String comentario;

  private boolean ativa;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  protected Avaliacao() {
    // exigido pela JPA
  }

  /** RF-19.2/RF-19.4 — avaliação real, autor deliberado. */
  public Avaliacao(UUID id, UUID pedidoId, UUID autorId, UUID alvoId, int nota, String comentario) {
    this.id = id;
    this.pedidoId = pedidoId;
    this.autorId = autorId;
    this.alvoId = alvoId;
    this.nota = (short) nota;
    this.comentario = comentario;
    this.ativa = true;
  }

  /** RF-19.5 — padrão positivo do job: nasce {@code ativa = false}, sem comentário. */
  public static Avaliacao padraoPositivo(UUID id, UUID pedidoId, UUID autorId, UUID alvoId) {
    Avaliacao avaliacao = new Avaliacao(id, pedidoId, autorId, alvoId, 5, null);
    avaliacao.ativa = false;
    return avaliacao;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPedidoId() {
    return pedidoId;
  }

  public UUID getAutorId() {
    return autorId;
  }

  public UUID getAlvoId() {
    return alvoId;
  }

  public int getNota() {
    return nota;
  }

  public String getComentario() {
    return comentario;
  }

  public boolean isAtiva() {
    return ativa;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }
}
