package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.service.SanctionExpiryJob;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.UserStatus;
import com.uaiou.users.repository.SancaoRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RF-07.7 — critério de aceite 7. Insere uma suspensão já vencida direto via SQL (a API não deixa
 * criar uma com {@code expiresAt} no passado, de propósito) e chama o job diretamente, sem esperar
 * o {@code @Scheduled}.
 */
class SanctionExpiryJobIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private SanctionExpiryJob sanctionExpiryJob;
  @Autowired private UsuarioRepository usuarioRepository;
  @Autowired private SancaoRepository sancaoRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void expiredSuspensionsAreClosedAndTheAccountReturnsToActive() {
    RegisteredTestUser courier = registerAndActivateCourier();
    var admin = moderation.createLoginableAdmin("senha-forte-o-suficiente");
    UUID sanctionId = UuidV7.next();
    jdbcTemplate.update("update usuario set status = 'suspenso' where id = ?", courier.id());
    jdbcTemplate.update(
        "insert into sancao (id, usuario_alvo_id, admin_id, tipo, motivo, fim, ativa) values (?, ?, ?, 'suspensao', ?, ?, true)",
        sanctionId,
        courier.id(),
        admin.id(),
        "Vencida há muito tempo.",
        Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));

    sanctionExpiryJob.encerrarSuspensoesVencidas();

    assertThat(usuarioRepository.findById(courier.id()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.ACTIVE);
    assertThat(sancaoRepository.findById(sanctionId).orElseThrow().isAtiva()).isFalse();
  }

  @Test
  void aSuspensionThatHasNotExpiredYetIsUntouched() {
    RegisteredTestUser courier = registerAndActivateCourier();
    var admin = moderation.createLoginableAdmin("senha-forte-o-suficiente");
    UUID sanctionId = UuidV7.next();
    jdbcTemplate.update("update usuario set status = 'suspenso' where id = ?", courier.id());
    jdbcTemplate.update(
        "insert into sancao (id, usuario_alvo_id, admin_id, tipo, motivo, fim, ativa) values (?, ?, ?, 'suspensao', ?, ?, true)",
        sanctionId,
        courier.id(),
        admin.id(),
        "Ainda vale.",
        Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)));

    sanctionExpiryJob.encerrarSuspensoesVencidas();

    assertThat(usuarioRepository.findById(courier.id()).orElseThrow().getStatus())
        .isEqualTo(UserStatus.SUSPENDED);
    assertThat(sancaoRepository.findById(sanctionId).orElseThrow().isAtiva()).isTrue();
  }
}
