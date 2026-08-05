package com.uaiou.delivery.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.delivery.dto.PayablesResponse;
import com.uaiou.delivery.service.MyPayablesService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /me/payables} (RF-18.6) — espelho do estabelecimento. */
@RestController
@RequestMapping("/me/payables")
public class MyPayablesController {

  private final MyPayablesService myPayablesService;
  private final CurrentUserHolder currentUserHolder;

  public MyPayablesController(
      MyPayablesService myPayablesService, CurrentUserHolder currentUserHolder) {
    this.myPayablesService = myPayablesService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public PayablesResponse get() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException("MERCHANT_ONLY", "Esta rota é do estabelecimento.");
    }
    return myPayablesService.get(user.userId());
  }
}
