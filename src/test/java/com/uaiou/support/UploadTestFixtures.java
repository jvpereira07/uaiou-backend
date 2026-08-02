package com.uaiou.support;

import com.uaiou.shared.id.UuidV7;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Insere linhas de {@code upload} direto no banco — a rota real de upload (POST/PUT /uploads) é do
 * T-05, ainda não implementada. Testes de campo verificado (PATCH /me, T-04) precisam de um upload
 * "pronto" para exercitar o fluxo real de validação sem depender de T-05 existir.
 */
@Component
public class UploadTestFixtures {

  private final JdbcTemplate jdbcTemplate;

  public UploadTestFixtures(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID createReadyUpload(UUID usuarioId, String purpose) {
    return insert(usuarioId, purpose, "ready");
  }

  public UUID createAwaitingUpload(UUID usuarioId, String purpose) {
    return insert(usuarioId, purpose, "awaiting_upload");
  }

  private UUID insert(UUID usuarioId, String purpose, String status) {
    UUID id = UuidV7.next();
    jdbcTemplate.update(
        "insert into upload (id, usuario_id, purpose, content_type, size_bytes, status, object_key, expira_em) "
            + "values (?, ?, ?, 'image/jpeg', 12345, ?, ?, now() + interval '1 hour')",
        id,
        usuarioId,
        purpose,
        status,
        "test-object-" + id);
    return id;
  }
}
