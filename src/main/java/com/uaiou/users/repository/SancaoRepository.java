package com.uaiou.users.repository;

import com.uaiou.users.entity.Sancao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SancaoRepository extends JpaRepository<Sancao, UUID> {

  /**
   * Sanção que efetivamente bloqueia o usuário agora: {@code ativa = true} e, se tiver prazo
   * (suspensão), ainda não vencido. Filtrar por prazo aqui — não só por {@code ativa} — importa
   * porque o job que encerra suspensão vencida (T-07) ainda não existe; sem essa checagem, uma
   * suspensão expirada bloquearia para sempre.
   */
  @Query(
      "select s from Sancao s where s.usuarioAlvoId = :usuarioId and s.ativa = true and (s.fim is null or s.fim > current_timestamp)")
  Optional<Sancao> findBlockingSanction(@Param("usuarioId") UUID usuarioId);
}
