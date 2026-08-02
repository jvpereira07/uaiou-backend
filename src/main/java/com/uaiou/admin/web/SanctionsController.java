package com.uaiou.admin.web;

import com.uaiou.admin.dto.CreateSanctionRequest;
import com.uaiou.admin.dto.SanctionSummary;
import com.uaiou.admin.service.SanctionService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/admin.md} cobertos por RF-07.5/RF-07.7 (T-07). */
@RestController
@RequestMapping("/admin")
public class SanctionsController {

  private final SanctionService sanctionService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public SanctionsController(
      SanctionService sanctionService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.sanctionService = sanctionService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @PostMapping("/users/{id}/sanctions")
  @ResponseStatus(HttpStatus.CREATED)
  public SanctionSummary create(
      @PathVariable UUID id, @Valid @RequestBody CreateSanctionRequest request) {
    AuthenticatedUser admin = requireAdmin();
    return sanctionService.apply(admin.userId(), id, request);
  }

  @DeleteMapping("/sanctions/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void reactivate(@PathVariable UUID id) {
    AuthenticatedUser admin = requireAdmin();
    sanctionService.reactivate(admin.userId(), id);
  }

  private AuthenticatedUser requireAdmin() {
    AuthenticatedUser user = currentUserHolder.require();
    adminAccessGuard.require(user);
    return user;
  }
}
