package com.uaiou.admin.web;

import com.uaiou.admin.dto.AdminOrderDetail;
import com.uaiou.admin.service.AdminOrderService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /admin/orders/{id}} (RF-21.8) — espelho administrativo, restrito ao admin. */
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

  @GetMapping("/{id}")
  public AdminOrderDetail get(@PathVariable UUID id) {
    AuthenticatedUser admin = currentUserHolder.require();
    adminAccessGuard.require(admin);
    return adminOrderService.get(id);
  }
}
