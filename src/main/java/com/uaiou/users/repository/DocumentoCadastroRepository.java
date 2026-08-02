package com.uaiou.users.repository;

import com.uaiou.uploads.Purpose;
import com.uaiou.users.DocumentApprovalStatus;
import com.uaiou.users.entity.DocumentoCadastro;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoCadastroRepository extends JpaRepository<DocumentoCadastro, UUID> {

  boolean existsByUploadId(UUID uploadId);

  /**
   * RF-06.1: o conjunto "vigente" — no máximo uma linha por (usuário, tipo), o histórico fica de
   * fora.
   */
  List<DocumentoCadastro> findByUsuarioIdAndStatusAprovacaoNot(
      UUID usuarioId, DocumentApprovalStatus statusAprovacao);

  Optional<DocumentoCadastro> findByUsuarioIdAndTipoAndStatusAprovacaoNot(
      UUID usuarioId, Purpose tipo, DocumentApprovalStatus statusAprovacao);
}
