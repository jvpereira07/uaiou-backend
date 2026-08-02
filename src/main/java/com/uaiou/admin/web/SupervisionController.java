package com.uaiou.admin.web;

import com.uaiou.admin.dto.AdminUserDetail;
import com.uaiou.admin.dto.CourierSummary;
import com.uaiou.admin.dto.MerchantSummary;
import com.uaiou.admin.service.SupervisionService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.PagingRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/admin.md} cobertos por RF-07.8 (T-07). */
@RestController
@RequestMapping("/admin")
public class SupervisionController {

  private final SupervisionService supervisionService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public SupervisionController(
      SupervisionService supervisionService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.supervisionService = supervisionService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @GetMapping("/couriers")
  public PageResponse<CourierSummary> couriers(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    requireAdmin();
    return supervisionService.listCouriers(
        status, search, PagingRequest.of(page, perPage), "/api/v1/admin/couriers");
  }

  @GetMapping("/merchants")
  public PageResponse<MerchantSummary> merchants(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    requireAdmin();
    return supervisionService.listMerchants(
        status, search, PagingRequest.of(page, perPage), "/api/v1/admin/merchants");
  }

  @GetMapping("/users/{id}")
  public AdminUserDetail userDetail(@PathVariable UUID id) {
    requireAdmin();
    return supervisionService.getUserDetail(id);
  }

  private void requireAdmin() {
    AuthenticatedUser user = currentUserHolder.require();
    adminAccessGuard.require(user);
  }
}
