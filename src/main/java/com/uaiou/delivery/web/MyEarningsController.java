package com.uaiou.delivery.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.delivery.dto.EarningsResponse;
import com.uaiou.delivery.dto.SettlementRequest;
import com.uaiou.delivery.dto.SettlementResponse;
import com.uaiou.delivery.service.MyEarningsService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code /me/earnings*} (RF-18.3/RF-18.4) — visão do entregador sobre o livro-razão. */
@RestController
@RequestMapping("/me/earnings")
public class MyEarningsController {

  private final MyEarningsService myEarningsService;
  private final CurrentUserHolder currentUserHolder;

  public MyEarningsController(
      MyEarningsService myEarningsService, CurrentUserHolder currentUserHolder) {
    this.myEarningsService = myEarningsService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public EarningsResponse get(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    return myEarningsService.get(requireCourier().userId(), PagingRequest.of(page, perPage));
  }

  @PostMapping("/settlements")
  public SettlementResponse settle(@Valid @RequestBody SettlementRequest request) {
    return myEarningsService.settle(requireCourier().userId(), request);
  }

  private AuthenticatedUser requireCourier() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException("COURIER_ONLY", "Esta rota é do entregador.");
    }
    return user;
  }
}
