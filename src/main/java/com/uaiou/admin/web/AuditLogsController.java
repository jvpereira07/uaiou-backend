package com.uaiou.admin.web;

import com.uaiou.admin.dto.AuditLogEntry;
import com.uaiou.admin.service.AuditService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.PagingRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF-07.9 — trilha de auditoria, somente leitura. Não existe (e nunca vai existir) um {@code
 * PUT}/{@code DELETE} aqui: {@link com.uaiou.admin.repository.RegistroAuditoriaRepository} nem
 * expõe os métodos que permitiriam implementar um.
 */
@RestController
@RequestMapping("/admin/audit-logs")
public class AuditLogsController {

  private final AuditService auditService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public AuditLogsController(
      AuditService auditService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.auditService = auditService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @GetMapping
  public PageResponse<AuditLogEntry> list(
      @RequestParam(required = false) UUID adminId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) UUID referenceId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    AuthenticatedUser user = currentUserHolder.require();
    adminAccessGuard.require(user);
    return auditService.search(
        adminId,
        action,
        referenceId,
        from,
        to,
        PagingRequest.of(page, perPage),
        "/api/v1/admin/audit-logs");
  }
}
