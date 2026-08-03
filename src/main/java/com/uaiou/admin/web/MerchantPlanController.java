package com.uaiou.admin.web;

import com.uaiou.admin.dto.AssignPlanRequest;
import com.uaiou.admin.service.MerchantPlanService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.credits.dto.SubscriptionSummary;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-09.2/RF-09.3 (T-09) — atribuição/troca de plano do estabelecimento, restrita ao admin. */
@RestController
@RequestMapping("/admin/merchants")
public class MerchantPlanController {

  private final MerchantPlanService merchantPlanService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public MerchantPlanController(
      MerchantPlanService merchantPlanService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.merchantPlanService = merchantPlanService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @PutMapping("/{id}/plan")
  public SubscriptionSummary assign(
      @PathVariable UUID id, @Valid @RequestBody AssignPlanRequest request) {
    AuthenticatedUser admin = currentUserHolder.require();
    adminAccessGuard.require(admin);
    return merchantPlanService.assign(admin.userId(), id, request.planId());
  }
}
