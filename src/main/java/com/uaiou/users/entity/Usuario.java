package com.uaiou.users.entity;

import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Tabela base do CTI (V1__identidade.sql): identidade, autenticação e status de moderação comuns
 * aos três tipos de usuário. {@link Estabelecimento}/{@link Entregador}/{@link Admin} têm {@code
 * usuario_id} como chave primária E estrangeira ao mesmo tempo (shared primary key) — ver
 * modelo-de-dominio.md.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

  @Id private UUID id;

  private String login;

  private String email;

  @Column(name = "senha_hash")
  private String senhaHash;

  @Column(name = "google_id")
  private String googleId;

  private Role tipo;

  @Column(name = "nome_exibicao")
  private String nomeExibicao;

  private String telefone;

  private UserStatus status;

  @CreationTimestamp
  @Column(name = "criado_em")
  private Instant criadoEm;

  // Coluna mapeada (não "insertable=false"/omitida) para o Hibernate sempre enviar um valor
  // explícito no
  // INSERT — se a coluna ficasse de fora da instrução, o DEFAULT now() do Postgres se aplicaria,
  // mas como
  // o campo É mapeado, um NULL explícito (ausência de valor no lado da aplicação) vence o DEFAULT e
  // viola o
  // NOT NULL. @CreationTimestamp/@UpdateTimestamp garantem que o valor nunca fica nulo.
  @UpdateTimestamp
  @Column(name = "atualizado_em")
  private Instant atualizadoEm;

  protected Usuario() {
    // exigido pela JPA
  }

  public Usuario(
      UUID id,
      String login,
      String email,
      String senhaHash,
      String googleId,
      Role tipo,
      String nomeExibicao) {
    this.id = id;
    this.login = login;
    this.email = email;
    this.senhaHash = senhaHash;
    this.googleId = googleId;
    this.tipo = tipo;
    this.nomeExibicao = nomeExibicao;
    this.status = UserStatus.PENDING;
  }

  public UUID getId() {
    return id;
  }

  public String getLogin() {
    return login;
  }

  public String getEmail() {
    return email;
  }

  public String getSenhaHash() {
    return senhaHash;
  }

  public String getGoogleId() {
    return googleId;
  }

  public Role getTipo() {
    return tipo;
  }

  public String getNomeExibicao() {
    return nomeExibicao;
  }

  public String getTelefone() {
    return telefone;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getCriadoEm() {
    return criadoEm;
  }

  /** Usado por {@code PasswordResetService} (RF-03.11). Não há setter genérico de propósito. */
  public void alterarSenha(String novoSenhaHash) {
    this.senhaHash = novoSenhaHash;
  }

  /** Campo livre (RF-04.3, T-04) — aplica direto, sem passar por moderação. */
  public void atualizarNomeExibicao(String nomeExibicao) {
    this.nomeExibicao = nomeExibicao;
  }

  /** Campo livre (RF-04.3, T-04) — aplica direto, sem passar por moderação. */
  public void atualizarTelefone(String telefone) {
    this.telefone = telefone;
  }

  /**
   * RF-04.3 (edição de campo verificado) e RF-06.4 (reenvio de documento) — as duas chamam isto ao
   * criar um {@code documento_cadastro} novo: a submissão precisa ser reavaliada, então o cadastro
   * volta à fila. Sem efeito em {@code SUSPENDED}/{@code BANNED} de propósito — sanção é decisão de
   * moderação independente de documento, reenviar um arquivo não deveria conseguir levantá-la
   * silenciosamente (e, na prática, o middleware de escrita já bloqueia esses dois status antes
   * desta chamada acontecer).
   */
  public void reabrirModeracaoSeNecessario() {
    if (status == UserStatus.ACTIVE || status == UserStatus.REJECTED) {
      status = UserStatus.PENDING;
    }
  }

  /** RF-07.4 — admin aprova o cadastro pendente: libera a operação (RN-11.1). */
  public void aprovar() {
    this.status = UserStatus.ACTIVE;
  }

  /**
   * RF-07.4 — admin rejeita o cadastro: fica {@code rejeitado}, distinto de {@code pendente}
   * (usuário precisa ver o motivo e agir), até o reenvio via {@link
   * #reabrirModeracaoSeNecessario()} recolocar na fila.
   */
  public void rejeitar() {
    this.status = UserStatus.REJECTED;
  }

  /**
   * RF-07.5 — suspensão (com prazo, encerrada pelo job de expiração ou por reativação antecipada).
   */
  public void suspender() {
    this.status = UserStatus.SUSPENDED;
  }

  /** RF-07.5 — banimento (sem prazo, só reverte por decisão do admin). */
  public void banir() {
    this.status = UserStatus.BANNED;
  }

  /** RF-07.7 — encerramento de sanção (antecipado ou por expiração) devolve a conta a ativo. */
  public void reativar() {
    this.status = UserStatus.ACTIVE;
  }
}
