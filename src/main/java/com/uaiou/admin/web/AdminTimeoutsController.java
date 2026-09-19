package com.uaiou.admin.web;

import com.uaiou.admin.dto.TimeoutOccurrenceResponse;
import com.uaiou.admin.dto.TimeoutRunResponse;
import com.uaiou.admin.dto.TimeoutSettingResponse;
import com.uaiou.admin.dto.UpdateTimeoutRequest;
import com.uaiou.admin.service.AdminTimeoutService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.timeout.OrderTimeoutRule;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.PagingRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Regras de timeout do ciclo de vida do pedido (V26), restritas ao admin. */
@RestController
@RequestMapping("/admin/timeouts")
public class AdminTimeoutsController {

  private final AdminTimeoutService adminTimeoutService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public AdminTimeoutsController(
      AdminTimeoutService adminTimeoutService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.adminTimeoutService = adminTimeoutService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @GetMapping
  public List<TimeoutSettingResponse> list() {
    requireAdmin();
    return adminTimeoutService.list();
  }

  @PutMapping("/{rule}")
  public TimeoutSettingResponse update(
      @PathVariable String rule, @Valid @RequestBody UpdateTimeoutRequest request) {
    AuthenticatedUser admin = requireAdmin();
    return adminTimeoutService.update(admin.userId(), parse(rule), request);
  }

  @PostMapping("/run")
  public TimeoutRunResponse run() {
    AuthenticatedUser admin = requireAdmin();
    return adminTimeoutService.runNow(admin.userId());
  }

  @GetMapping("/occurrences")
  public PageResponse<TimeoutOccurrenceResponse> occurrences(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    requireAdmin();
    return adminTimeoutService.occurrences(
        PagingRequest.of(page, perPage), "/api/v1/admin/timeouts/occurrences");
  }

  private static OrderTimeoutRule parse(String rule) {
    try {
      return OrderTimeoutRule.fromCode(rule);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("UNKNOWN_TIMEOUT_RULE", "Regra de timeout desconhecida.");
    }
  }

  private AuthenticatedUser requireAdmin() {
    AuthenticatedUser user = currentUserHolder.require();
    adminAccessGuard.require(user);
    return user;
  }
}
