package com.uaiou.admin.web;

import com.uaiou.admin.dto.FinancialAdjustmentRequest;
import com.uaiou.admin.dto.FinancialAdjustmentResponse;
import com.uaiou.admin.service.FinancialAdjustmentService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RF-09.10 (T-09) — {@code POST /admin/financial-adjustments}, restrito ao admin. */
@RestController
@RequestMapping("/admin/financial-adjustments")
public class FinancialAdjustmentsController {

  private final FinancialAdjustmentService financialAdjustmentService;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public FinancialAdjustmentsController(
      FinancialAdjustmentService financialAdjustmentService,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.financialAdjustmentService = financialAdjustmentService;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public FinancialAdjustmentResponse create(
      @Valid @RequestBody FinancialAdjustmentRequest request) {
    AuthenticatedUser admin = currentUserHolder.require();
    adminAccessGuard.require(admin);
    return financialAdjustmentService.create(admin.userId(), request);
  }
}
