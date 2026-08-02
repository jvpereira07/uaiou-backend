package com.uaiou.admin.service;

import com.uaiou.admin.dto.AuditLogEntry;
import com.uaiou.admin.entity.RegistroAuditoria;
import com.uaiou.admin.repository.RegistroAuditoriaRepository;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.Paginator;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-07.1: componente único que grava {@code registro_auditoria} — toda escrita administrativa
 * passa por aqui, dentro da mesma transação do efeito (nenhuma propagação especial: se a operação
 * que chamou {@link #record} reverter, a auditoria reverte junto — é exatamente o que o critério de
 * aceite 4 verifica).
 */
@Service
public class AuditService {

  private final RegistroAuditoriaRepository registroAuditoriaRepository;
  private final UsuarioRepository usuarioRepository;

  public AuditService(
      RegistroAuditoriaRepository registroAuditoriaRepository,
      UsuarioRepository usuarioRepository) {
    this.registroAuditoriaRepository = registroAuditoriaRepository;
    this.usuarioRepository = usuarioRepository;
  }

  @Transactional
  public void record(
      UUID adminId, String acao, String referenciaTipo, UUID referenciaId, String motivo) {
    record(adminId, acao, referenciaTipo, referenciaId, motivo, null);
  }

  @Transactional
  public void record(
      UUID adminId,
      String acao,
      String referenciaTipo,
      UUID referenciaId,
      String motivo,
      BigDecimal valor) {
    registroAuditoriaRepository.save(
        new RegistroAuditoria(
            UuidV7.next(), adminId, acao, referenciaTipo, referenciaId, motivo, valor));
  }

  /** RF-07.9 — filtros por admin, ação, referência e período (api/admin.md). */
  @Transactional(readOnly = true)
  public PageResponse<AuditLogEntry> search(
      UUID adminId,
      String action,
      UUID referenceId,
      Instant from,
      Instant to,
      PagingRequest paging,
      String baseUri) {
    Specification<RegistroAuditoria> spec = filter(adminId, action, referenceId, from, to);
    Page<RegistroAuditoria> page =
        registroAuditoriaRepository.findAll(
            spec,
            PageRequest.of(paging.page() - 1, paging.perPage(), Sort.by("criadoEm").descending()));

    Map<UUID, String> adminNames =
        usuarioRepository
            .findAllById(
                page.getContent().stream().map(RegistroAuditoria::getAdminId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Usuario::getId, Usuario::getNomeExibicao));

    List<AuditLogEntry> content =
        page.getContent().stream().map(r -> toEntry(r, adminNames)).toList();
    return Paginator.paginate(content, page.getTotalElements(), paging, baseUri);
  }

  private AuditLogEntry toEntry(RegistroAuditoria r, Map<UUID, String> adminNames) {
    AuditLogEntry.ReferenceRef reference =
        r.getReferenciaTipo() == null
            ? null
            : new AuditLogEntry.ReferenceRef(r.getReferenciaTipo(), r.getReferenciaId());
    return new AuditLogEntry(
        r.getId(),
        r.getAcao(),
        new AuditLogEntry.AdminRef(r.getAdminId(), adminNames.get(r.getAdminId())),
        r.getValor(),
        r.getMotivo(),
        reference,
        r.getCriadoEm());
  }

  private Specification<RegistroAuditoria> filter(
      UUID adminId, String action, UUID referenceId, Instant from, Instant to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (adminId != null) {
        predicates.add(cb.equal(root.get("adminId"), adminId));
      }
      if (action != null) {
        predicates.add(cb.equal(root.get("acao"), action));
      }
      if (referenceId != null) {
        predicates.add(cb.equal(root.get("referenciaId"), referenceId));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("criadoEm"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("criadoEm"), to));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
