package com.uaiou.support;

import com.uaiou.shared.id.UuidV7;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Manipula moderação (ativação, suspensão, banimento) direto no banco via SQL — aprovação de
 * cadastro e aplicação de sanção são do T-07 (ainda não implementadas), mas os testes de T-03
 * precisam desses estados de conta para exercitar RF-03.4/03.9.
 */
@Component
public class UserModerationTestFixtures {

  private final JdbcTemplate jdbcTemplate;

  public UserModerationTestFixtures(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void activate(UUID usuarioId) {
    jdbcTemplate.update("update usuario set status = 'ativo' where id = ?", usuarioId);
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
    UUID adminId = UuidV7.next();
    // UUID.randomUUID() (v4) aqui de propósito, não UuidV7: os primeiros bits de um v7 são
    // timestamp, então
    // duas chamadas dentro do mesmo milissegundo colidiriam em "login" se o sufixo viesse do
    // próprio adminId.
    String suffix = UUID.randomUUID().toString();
    jdbcTemplate.update(
        "insert into usuario (id, login, email, senha_hash, tipo, nome_exibicao, status) values (?, ?, ?, 'x', 'admin', 'Admin de teste', 'ativo')",
        adminId,
        "admin-teste-" + suffix,
        "admin-teste-" + suffix + "@uaiou.test");
    jdbcTemplate.update("insert into admin (usuario_id, nivel) values (?, 'pleno')", adminId);
    return adminId;
  }
}
