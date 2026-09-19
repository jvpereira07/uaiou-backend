package com.uaiou.admin.web;

import com.uaiou.admin.dto.BehaviorLimitResponse;
import com.uaiou.admin.dto.UpdateBehaviorLimitRequest;
import com.uaiou.admin.service.AdminBehaviorLimitService;
import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.limits.BehaviorLimitRule;
import com.uaiou.shared.error.BadRequestException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Limites de comportamento (V27): "N ocorrências na janela bloqueiam por um tempo". */
@RestController
@RequestMapping("/admin/limits")
public class AdminBehaviorLimitsController {

  private final AdminBehaviorLimitService service;
  private final CurrentUserHolder currentUserHolder;
  private final AdminAccessGuard adminAccessGuard;

  public AdminBehaviorLimitsController(
      AdminBehaviorLimitService service,
      CurrentUserHolder currentUserHolder,
      AdminAccessGuard adminAccessGuard) {
    this.service = service;
    this.currentUserHolder = currentUserHolder;
    this.adminAccessGuard = adminAccessGuard;
  }

  @GetMapping
  public List<BehaviorLimitResponse> list() {
    requireAdmin();
    return service.list();
  }

  @PutMapping("/{rule}")
  public BehaviorLimitResponse update(
      @PathVariable String rule, @Valid @RequestBody UpdateBehaviorLimitRequest request) {
    AuthenticatedUser admin = requireAdmin();
    BehaviorLimitRule regra;
    try {
      regra = BehaviorLimitRule.fromCode(rule);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("UNKNOWN_LIMIT_RULE", "Limite desconhecido.");
    }
    return service.update(admin.userId(), regra, request);
  }

  private AuthenticatedUser requireAdmin() {
    AuthenticatedUser user = currentUserHolder.require();
    adminAccessGuard.require(user);
    return user;
  }
}
