package com.uaiou.admin.web;

import com.uaiou.admin.dto.PendingRegistrationSummary;
import com.uaiou.admin.dto.RegistrationReviewResponse;
import com.uaiou.admin.dto.ReviewRegistrationRequest;
import com.uaiou.admin.service.RegistrationReviewService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.PagingRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/admin.md} cobertos por RF-07.3/RF-07.4 (T-07). */
@RestController
@RequestMapping("/admin/registrations")
public class RegistrationsController {

  private final RegistrationReviewService registrationReviewService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public RegistrationsController(
      RegistrationReviewService registrationReviewService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.registrationReviewService = registrationReviewService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @GetMapping
  public PageResponse<PendingRegistrationSummary> queue(
      @RequestParam(defaultValue = "pending") String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    AuthenticatedUser admin = requireAdmin();
    if (!"pending".equals(status)) {
      throw new BadRequestException(
          "UNSUPPORTED_STATUS", "Só \"pending\" é suportado para este filtro.");
    }
    return registrationReviewService.queue(
        admin.userId(),
        PagingRequest.of(page, perPage),
        "/api/v1/admin/registrations?status=pending");
  }

  @PutMapping("/{userId}/review")
  public RegistrationReviewResponse review(
      @PathVariable UUID userId, @Valid @RequestBody ReviewRegistrationRequest request) {
    AuthenticatedUser admin = requireAdmin();
    return registrationReviewService.review(admin.userId(), userId, request);
  }

  private AuthenticatedUser requireAdmin() {
    AuthenticatedUser user = currentUserHolder.require();
    adminAccessGuard.require(user);
    return user;
  }
}
