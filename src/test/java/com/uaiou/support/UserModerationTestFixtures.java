package com.uaiou.support;

import com.uaiou.shared.id.UuidV7;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Manipula moderação (ativação, suspensão, banimento) direto no banco via SQL — os testes precisam
 * desses estados de conta sem depender das próprias rotas de escrita que os produzem (RF-03.4/03.9,
 * T-06). Desde T-07 as rotas de escrita existem de verdade (ver {@link com.uaiou.admin}); esta
 * fixture continua útil para simular o estado *anterior* a uma ação em teste (ex.: "usuário já
 * rejeitado" antes de testar o reenvio) e para casos fora do escopo de T-07 (nenhum ainda).
 */
@Component
public class UserModerationTestFixtures {

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;

  public UserModerationTestFixtures(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
    this.jdbcTemplate = jdbcTemplate;
    this.passwordEncoder = passwordEncoder;
  }

  public record TestAdmin(UUID id, String login) {}

  /** T-07: admin de teste com senha real, para logar via {@code POST /auth/sessions}. */
  public TestAdmin createLoginableAdmin(String password) {
    UUID adminId = UuidV7.next();
    String suffix = UUID.randomUUID().toString();
    String login = "admin-teste-" + suffix;
    jdbcTemplate.update(
        "insert into usuario (id, login, email, senha_hash, tipo, nome_exibicao, status) values (?, ?, ?, ?, 'admin', 'Admin de teste', 'ativo')",
        adminId,
        login,
        login + "@uaiou.test",
        passwordEncoder.encode(password));
    jdbcTemplate.update("insert into admin (usuario_id, nivel) values (?, 'pleno')", adminId);
    return new TestAdmin(adminId, login);
  }

  public void activate(UUID usuarioId) {
    jdbcTemplate.update("update usuario set status = 'ativo' where id = ?", usuarioId);
  }

  /**
   * T-06: simula a decisão do admin (T-07, ainda não implementada) rejeitando um documento
   * específico.
   */
  public void rejectDocument(UUID usuarioId, UUID documentoCadastroId, String motivo) {
    jdbcTemplate.update("update usuario set status = 'rejeitado' where id = ?", usuarioId);
    jdbcTemplate.update(
        "update documento_cadastro set status_aprovacao = 'rejeitado', motivo_rejeicao = ? where id = ?",
        motivo,
        documentoCadastroId);
  }

  /**
   * T-06: simula a decisão do admin (T-07, ainda não implementada) aprovando um documento
   * específico.
   */
  public void approveDocument(UUID documentoCadastroId) {
    jdbcTemplate.update(
        "update documento_cadastro set status_aprovacao = 'aprovado' where id = ?",
        documentoCadastroId);
  }

  public void ban(UUID usuarioId, String motivo) {
    applySanction(usuarioId, "banido", "banimento", motivo, null);
  }

  public void suspend(UUID usuarioId, String motivo, Instant fim) {
    applySanction(usuarioId, "suspenso", "suspensao", motivo, fim);
  }

  private void applySanction(
      UUID usuarioId, String status, String tipo, String motivo, Instant fim) {
    UUID adminUsuarioId = createTestAdmin();
    jdbcTemplate.update("update usuario set status = ? where id = ?", status, usuarioId);
    jdbcTemplate.update(
        "insert into sancao (id, usuario_alvo_id, admin_id, tipo, motivo, fim, ativa) values (?, ?, ?, ?, ?, ?, true)",
        UuidV7.next(),
        usuarioId,
        adminUsuarioId,
        tipo,
        motivo,
        fim == null ? null : Timestamp.from(fim));
  }

  private UUID createTestAdmin() {
    return createLoginableAdmin(UUID.randomUUID().toString()).id();
  }
}
