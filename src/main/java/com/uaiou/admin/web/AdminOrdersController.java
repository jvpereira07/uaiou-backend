package com.uaiou.admin.web;

import com.uaiou.admin.dto.AdminOrderActionRequest;
import com.uaiou.admin.dto.AdminOrderDetail;
import com.uaiou.admin.dto.AdminOrderSummary;
import com.uaiou.admin.dto.AdminOrdersOverview;
import com.uaiou.admin.service.AdminOrderService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.PagingRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espelho administrativo (RF-21.8), histórico de entregas do sistema e intervenção manual no estado
 * do pedido — tudo restrito ao admin.
 */
@RestController
@RequestMapping("/admin/orders")
public class AdminOrdersController {

  private final AdminOrderService adminOrderService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public AdminOrdersController(
      AdminOrderService adminOrderService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.adminOrderService = adminOrderService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @GetMapping
  public PageResponse<AdminOrderSummary> list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID merchantId,
      @RequestParam(required = false) UUID courierId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    requireAdmin();
    return adminOrderService.list(
        status,
        search,
        merchantId,
        courierId,
        from,
        to,
        PagingRequest.of(page, perPage),
        "/api/v1/admin/orders");
  }

  @GetMapping("/overview")
  public AdminOrdersOverview overview() {
    requireAdmin();
    return adminOrderService.overview();
  }

  @GetMapping("/{id}")
  public AdminOrderDetail get(@PathVariable UUID id) {
    requireAdmin();
    return adminOrderService.get(id);
  }

  @PostMapping("/{id}/actions")
  public AdminOrderDetail act(
      @PathVariable UUID id, @Valid @RequestBody AdminOrderActionRequest request) {
    AuthenticatedUser admin = requireAdmin();
    return adminOrderService.act(admin.userId(), id, request);
  }

  private AuthenticatedUser requireAdmin() {
    AuthenticatedUser user = currentUserHolder.require();
    adminAccessGuard.require(user);
    return user;
  }
}
