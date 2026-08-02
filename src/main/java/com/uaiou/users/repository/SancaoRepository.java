package com.uaiou.users.repository;

import com.uaiou.users.entity.Sancao;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SancaoRepository extends JpaRepository<Sancao, UUID> {

  /**
   * Sanção que efetivamente bloqueia o usuário agora: {@code ativa = true} e, se tiver prazo
   * (suspensão), ainda não vencido. Filtrar por prazo aqui — não só por {@code ativa} — importa
   * porque {@link com.uaiou.admin.service.SanctionExpiryJob} roda em intervalo, não
   * instantaneamente quando o prazo vence; sem essa checagem, uma suspensão vencida bloquearia até
   * o próximo tick do job.
   */
  @Query(
      "select s from Sancao s where s.usuarioAlvoId = :usuarioId and s.ativa = true and (s.fim is null or s.fim > current_timestamp)")
  Optional<Sancao> findBlockingSanction(@Param("usuarioId") UUID usuarioId);

  /** RF-07.8: histórico de sanções na visão administrativa de um usuário. */
  List<Sancao> findByUsuarioAlvoIdOrderByCriadoEmDesc(UUID usuarioAlvoId);

  /**
   * RF-07.7: suspensões (nunca banimento — {@code fim} é sempre nulo nesse caso) com prazo vencido.
   */
  @Query("select s from Sancao s where s.ativa = true and s.fim is not null and s.fim <= :agora")
  List<Sancao> findExpired(@Param("agora") Instant agora);
}
