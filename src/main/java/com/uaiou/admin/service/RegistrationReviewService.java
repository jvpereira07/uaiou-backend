package com.uaiou.admin.service;

import com.uaiou.admin.dto.PendingRegistrationSummary;
import com.uaiou.admin.dto.RegistrationReviewResponse;
import com.uaiou.admin.dto.ReviewDecision;
import com.uaiou.admin.dto.ReviewRegistrationRequest;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.Paginator;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.uploads.service.UploadService;
import com.uaiou.users.DocumentApprovalStatus;
import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.DocumentoCadastro;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.DocumentoCadastroRepository;
import com.uaiou.users.repository.UsuarioRepository;
import com.uaiou.users.service.DocumentoCadastroService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-07.3/RF-07.4 — fila de aprovação de cadastro e a decisão do admin sobre ela. */
@Service
public class RegistrationReviewService {

  private final UsuarioRepository usuarioRepository;
  private final DocumentoCadastroRepository documentoCadastroRepository;
  private final DocumentoCadastroService documentoCadastroService;
  private final UploadService uploadService;
  private final AuditService auditService;

  public RegistrationReviewService(
      UsuarioRepository usuarioRepository,
      DocumentoCadastroRepository documentoCadastroRepository,
      DocumentoCadastroService documentoCadastroService,
      UploadService uploadService,
      AuditService auditService) {
    this.usuarioRepository = usuarioRepository;
    this.documentoCadastroRepository = documentoCadastroRepository;
    this.documentoCadastroService = documentoCadastroService;
    this.uploadService = uploadService;
    this.auditService = auditService;
  }

  /**
   * RF-07.3: só entra na fila quem já enviou todos os documentos exigidos pelo papel — um cadastro
   * {@code pendente} recém-criado, sem nenhum documento ainda, não tem o que o admin revisar
   * (critério de aceite 4 de T-06). Paginado em memória: volume esperado de v1 não justifica uma
   * query com join dinâmico contra o CTI + documentos por papel.
   */
  @Transactional(readOnly = true)
  public PageResponse<PendingRegistrationSummary> queue(
      UUID adminId, PagingRequest paging, String baseUri) {
    List<PendingRegistrationSummary> ready =
        usuarioRepository.findByStatus(UserStatus.PENDING).stream()
            .filter(documentoCadastroService::hasAllRequiredDocumentsSubmitted)
            .map(usuario -> toSummary(adminId, usuario))
            .toList();

    int from = Math.min(paging.offset(), ready.size());
    int to = Math.min(from + paging.perPage(), ready.size());
    return Paginator.paginate(ready.subList(from, to), ready.size(), paging, baseUri);
  }

  @Transactional
  public RegistrationReviewResponse review(
      UUID adminId, UUID targetUserId, ReviewRegistrationRequest request) {
    Usuario usuario = usuarioRepository.findById(targetUserId).orElseThrow(this::userNotFound);
    if (usuario.getStatus() != UserStatus.PENDING) {
      throw new ConflictException(
          "REGISTRATION_NOT_PENDING", "Este cadastro não está aguardando aprovação.");
    }

    List<DocumentoCadastro> current =
        documentoCadastroRepository.findByUsuarioIdAndStatusAprovacaoNot(
            targetUserId, DocumentApprovalStatus.SUPERSEDED);

    if (request.decision() == ReviewDecision.APPROVED) {
      if (!documentoCadastroService.hasAllRequiredDocumentsSubmitted(usuario)) {
        throw new ConflictException(
            "REGISTRATION_INCOMPLETE",
            "Faltam documentos exigidos para este papel; não é possível aprovar.");
      }
      current.forEach(doc -> doc.aprovar(adminId));
      usuario.aprovar();
      auditService.record(adminId, "aprovar_cadastro", "usuario", targetUserId, request.reason());
    } else {
      List<UUID> documentIds = request.documentIds() == null ? List.of() : request.documentIds();
      if (documentIds.isEmpty()) {
        throw new BadRequestException(
            "MISSING_FIELD", "\"documentIds\" é obrigatório para rejeitar um cadastro.");
      }
      for (UUID documentId : documentIds) {
        DocumentoCadastro doc =
            current.stream()
                .filter(d -> d.getId().equals(documentId))
                .findFirst()
                .orElseThrow(
                    () ->
                        new BadRequestException(
                            "DOCUMENT_NOT_FOUND",
                            "Documento \"" + documentId + "\" não pertence a este cadastro."));
        doc.rejeitar(adminId, request.reason());
      }
      usuario.rejeitar();
      auditService.record(adminId, "rejeitar_cadastro", "usuario", targetUserId, request.reason());
    }

    return new RegistrationReviewResponse(usuario.getId(), usuario.getStatus());
  }

  private PendingRegistrationSummary toSummary(UUID adminId, Usuario usuario) {
    List<PendingRegistrationSummary.DocumentReviewItem> documents =
        documentoCadastroRepository
            .findByUsuarioIdAndStatusAprovacaoNot(
                usuario.getId(), DocumentApprovalStatus.SUPERSEDED)
            .stream()
            .map(doc -> toReviewItem(adminId, doc))
            .toList();
    return new PendingRegistrationSummary(
        usuario.getId(),
        usuario.getTipo(),
        usuario.getNomeExibicao(),
        usuario.getEmail(),
        usuario.getCriadoEm(),
        documents);
  }

  private PendingRegistrationSummary.DocumentReviewItem toReviewItem(
      UUID adminId, DocumentoCadastro doc) {
    String fileUrl = uploadService.readMetadata(adminId, true, doc.getUploadId()).fileUrl();
    return new PendingRegistrationSummary.DocumentReviewItem(
        doc.getId(), doc.getTipo(), doc.getStatusAprovacao(), fileUrl);
  }

  private NotFoundException userNotFound() {
    return new NotFoundException("USER_NOT_FOUND", "Usuário não encontrado.");
  }
}
