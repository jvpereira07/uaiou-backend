package com.uaiou.admin.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.credits.dto.CreatePlanRequest;
import com.uaiou.credits.dto.PlanSummary;
import com.uaiou.credits.dto.UpdatePlanRequest;
import com.uaiou.credits.service.PlanoService;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.PagingRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-09.1 (T-09) — CRUD mínimo do catálogo de planos, restrito ao admin. */
@RestController
@RequestMapping("/admin/plans")
public class PlanCatalogController {

  private final PlanoService planoService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public PlanCatalogController(
      PlanoService planoService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.planoService = planoService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @GetMapping
  public PageResponse<PlanSummary> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    requireAdmin();
    return planoService.list(PagingRequest.of(page, perPage), "/api/v1/admin/plans");
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlanSummary create(@Valid @RequestBody CreatePlanRequest request) {
    requireAdmin();
    return planoService.create(request);
  }

  @PutMapping("/{id}")
  public PlanSummary update(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest request) {
    requireAdmin();
    return planoService.update(id, request);
  }

  private void requireAdmin() {
    AuthenticatedUser user = currentUserHolder.require();
    adminAccessGuard.require(user);
  }
}
