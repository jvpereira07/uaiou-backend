package com.uaiou.auth.repository;

import com.uaiou.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  List<RefreshToken> findByFamiliaId(UUID familiaId);

  /** Usado na troca de senha (RF-03.11) e na revogação total de sessões (T-04). */
  @Modifying
  @Query(
      "update RefreshToken r set r.revogadoEm = :agora where r.usuarioId = :usuarioId and r.revogadoEm is null")
  int revogarTodosDoUsuario(@Param("usuarioId") UUID usuarioId, @Param("agora") Instant agora);

  /**
   * {@code REQUIRES_NEW} de propósito (RF-03.8): quem chama isto ({@code
   * SessionService.refreshGrant}) lança {@code UnauthorizedException} logo em seguida para rejeitar
   * a requisição de reuso — e uma exceção não verificada aciona rollback por padrão na transação do
   * chamador. Sem uma transação própria aqui, essa revogação (a própria defesa contra token vazado)
   * seria desfeita junto com o rollback, e a família "revogada" continuaria válida silenciosamente.
   */
  @Modifying
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Query(
      "update RefreshToken r set r.revogadoEm = :agora where r.familiaId = :familiaId and r.revogadoEm is null")
  int revogarFamilia(@Param("familiaId") UUID familiaId, @Param("agora") Instant agora);
}
