package com.uaiou.notifications.repository;

import com.uaiou.notifications.entity.Notificacao;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

  Page<Notificacao> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId, Pageable pageable);

  Page<Notificacao> findByUsuarioIdAndLidaEmIsNullOrderByCriadoEmDesc(
      UUID usuarioId, Pageable pageable);

  Page<Notificacao> findByUsuarioIdAndTipoOrderByCriadoEmDesc(
      UUID usuarioId, String tipo, Pageable pageable);

  Page<Notificacao> findByUsuarioIdAndTipoAndLidaEmIsNullOrderByCriadoEmDesc(
      UUID usuarioId, String tipo, Pageable pageable);

  /**
   * RF-08.5 — {@code meta.unread} precisa bater com a contagem real, não com o que veio na página.
   */
  long countByUsuarioIdAndLidaEmIsNull(UUID usuarioId);

  Optional<Notificacao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

  List<Notificacao> findByUsuarioIdAndLidaEmIsNull(UUID usuarioId);

  /** Só para verificação em teste: o que foi realmente emitido para um tipo. */
  @Query("select n from Notificacao n where n.tipo = :tipo and n.usuarioId in :usuarioIds")
  List<Notificacao> findByTipoAndUsuarios(
      @Param("tipo") String tipo, @Param("usuarioIds") List<UUID> usuarioIds);

  @Modifying
  @Query("delete from Notificacao n where n.usuarioId = :usuarioId")
  void deleteAllDoUsuario(@Param("usuarioId") UUID usuarioId);
}
