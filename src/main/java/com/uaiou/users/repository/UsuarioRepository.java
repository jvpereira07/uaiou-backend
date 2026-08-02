package com.uaiou.users.repository;

import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.Usuario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository
    extends JpaRepository<Usuario, UUID>, JpaSpecificationExecutor<Usuario> {

  Optional<Usuario> findByLogin(String login);

  Optional<Usuario> findByGoogleId(String googleId);

  Optional<Usuario> findByEmail(String email);

  boolean existsByLogin(String login);

  boolean existsByEmail(String email);

  /** RF-07.3: base da fila de aprovação de cadastro — filtrada em memória por completude. */
  List<Usuario> findByStatus(UserStatus status);

  /**
   * Vincula o Google ao encontrar a conta pelo fallback de e-mail (RF-03.6) — via query direta, não
   * setter na entidade: é o único fluxo que muda esse campo, não vale abrir mutabilidade geral por
   * ele.
   */
  @Modifying
  @Query("update Usuario u set u.googleId = :googleId where u.id = :usuarioId")
  void linkGoogleId(@Param("usuarioId") UUID usuarioId, @Param("googleId") String googleId);
}
