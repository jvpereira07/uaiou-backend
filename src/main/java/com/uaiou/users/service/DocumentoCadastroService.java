package com.uaiou.users.service;

import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.UnauthorizedException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.uploads.Purpose;
import com.uaiou.uploads.service.UploadService;
import com.uaiou.users.DocumentApprovalStatus;
import com.uaiou.users.dto.DocumentSummary;
import com.uaiou.users.dto.DocumentsResponse;
import com.uaiou.users.dto.SubmitDocumentRequest;
import com.uaiou.users.entity.DocumentoCadastro;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.DocumentoCadastroRepository;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-06.1 a RF-06.6 — envio e reenvio dos documentos exigidos pelo papel do usuário. */
@Service
public class DocumentoCadastroService {

  private static final Logger log = LoggerFactory.getLogger(DocumentoCadastroService.class);

  private static final Set<Purpose> MERCHANT_REQUIRED_TYPES = Set.of(Purpose.CNPJ_DOCUMENT);

  private final UsuarioRepository usuarioRepository;
  private final EntregadorRepository entregadorRepository;
  private final DocumentoCadastroRepository documentoCadastroRepository;
  private final UploadService uploadService;

  public DocumentoCadastroService(
      UsuarioRepository usuarioRepository,
      EntregadorRepository entregadorRepository,
      DocumentoCadastroRepository documentoCadastroRepository,
      UploadService uploadService) {
    this.usuarioRepository = usuarioRepository;
    this.entregadorRepository = entregadorRepository;
    this.documentoCadastroRepository = documentoCadastroRepository;
    this.uploadService = uploadService;
  }

  @Transactional(readOnly = true)
  public DocumentsResponse list(UUID usuarioId) {
    Usuario usuario = requireUsuario(usuarioId);
    Set<Purpose> required = requiredTypesFor(usuario);

    List<DocumentoCadastro> current =
        documentoCadastroRepository.findByUsuarioIdAndStatusAprovacaoNot(
            usuarioId, DocumentApprovalStatus.SUPERSEDED);
    Set<Purpose> submitted =
        current.stream().map(DocumentoCadastro::getTipo).collect(Collectors.toSet());
    List<Purpose> missing = required.stream().filter(type -> !submitted.contains(type)).toList();

    return new DocumentsResponse(current.stream().map(this::toSummary).toList(), missing);
  }

  @Transactional
  public DocumentSummary submit(UUID usuarioId, SubmitDocumentRequest request) {
    Usuario usuario = requireUsuario(usuarioId);
    Set<Purpose> required = requiredTypesFor(usuario);
    if (!required.contains(request.type())) {
      throw new BadRequestException(
          "DOCUMENT_TYPE_NOT_APPLICABLE",
          "\"" + request.type() + "\" não é exigido para o seu tipo de conta.");
    }

    Optional<DocumentoCadastro> current =
        documentoCadastroRepository.findByUsuarioIdAndTipoAndStatusAprovacaoNot(
            usuarioId, request.type(), DocumentApprovalStatus.SUPERSEDED);
    if (current.isPresent()
        && current.get().getStatusAprovacao() == DocumentApprovalStatus.APPROVED) {
      // RF-06.5 — a exceção (campo verificado editado via T-04) não passa por este endpoint: ela
      // cria o
      // documento pendente direto em ProfileService, que já sabe a diferença.
      throw new ConflictException(
          "DOCUMENT_ALREADY_APPROVED", "Este documento já foi aprovado; não aceita novo envio.");
    }

    uploadService.validateForConsumption(request.uploadId(), usuarioId, request.type());
    if (documentoCadastroRepository.existsByUploadId(request.uploadId())) {
      throw new ConflictException(
          "UPLOAD_ALREADY_USED", "Este upload já foi usado em outro documento de cadastro.");
    }

    DocumentoCadastro created =
        new DocumentoCadastro(UuidV7.next(), usuarioId, request.type(), request.uploadId());
    try {
      documentoCadastroRepository.saveAndFlush(created);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "UPLOAD_ALREADY_USED", "Este upload já foi usado em outro documento de cadastro.");
    }

    // RF-06.4: o anterior (pendente ou rejeitado — aprovado já foi barrado acima) vira histórico,
    // nunca é
    // apagado, e o reenvio devolve o cadastro à fila.
    current.ifPresent(DocumentoCadastro::marcarSuperado);
    usuario.reabrirModeracaoSeNecessario();

    return toSummary(created);
  }

  private Set<Purpose> requiredTypesFor(Usuario usuario) {
    return switch (usuario.getTipo()) {
      case COURIER -> requiredTypesForCourier(usuario);
      case MERCHANT -> MERCHANT_REQUIRED_TYPES;
      case ADMIN -> Set.of();
    };
  }

  private Set<Purpose> requiredTypesForCourier(Usuario usuario) {
    Entregador entregador =
        entregadorRepository.findById(usuario.getId()).orElseThrow(() -> ctiCorruption(usuario));
    Set<Purpose> required = new LinkedHashSet<>();
    required.add(Purpose.IDENTITY_DOCUMENT);
    required.add(Purpose.VEHICLE_DOCUMENT);
    if (requiresDriverLicense(entregador.getVeiculoTipo())) {
      required.add(Purpose.DRIVER_LICENSE);
    }
    return required;
  }

  /**
   * RF-06.3 ("CNH quando o veículo exigir") — bicicleta não exige CNH no trânsito brasileiro.
   * {@code veiculoTipo} ainda não é um vocabulário fechado (T-03/T-04 não impuseram um enum), então
   * o padrão é conservador: qualquer valor que não seja claramente bicicleta exige CNH.
   */
  private boolean requiresDriverLicense(String veiculoTipo) {
    return veiculoTipo == null || !"BICYCLE".equalsIgnoreCase(veiculoTipo);
  }

  private DocumentSummary toSummary(DocumentoCadastro documento) {
    return new DocumentSummary(
        documento.getId(),
        documento.getTipo(),
        documento.getStatusAprovacao(),
        documento.getMotivoRejeicao(),
        documento.getCriadoEm());
  }

  private Usuario requireUsuario(UUID usuarioId) {
    return usuarioRepository
        .findById(usuarioId)
        .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Usuário não encontrado."));
  }

  /**
   * Mesma invariante do CTI de RF-04.5 (T-04) — a linha-filha tem que existir para o tipo
   * declarado.
   */
  private RuntimeException ctiCorruption(Usuario usuario) {
    log.error(
        "Invariante do CTI violada: usuario {} tem tipo={} sem linha-filha correspondente.",
        usuario.getId(),
        usuario.getTipo());
    return new IllegalStateException("Perfil corrompido para o usuário " + usuario.getId());
  }
}
