package com.uaiou.orders.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.dto.OrderLifecycleRequests.CancellationRequest;
import com.uaiou.orders.dto.OrderLifecycleRequests.PickupArrivalRequest;
import com.uaiou.orders.dto.OrderLifecycleRequests.WithdrawalRequest;
import com.uaiou.orders.dto.OrderLifecycleResponse;
import com.uaiou.orders.service.OrderCancellationService;
import com.uaiou.orders.service.PickupService;
import com.uaiou.orders.service.WithdrawalService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** T-26 — coleta, cancelamento e desistência. O papel decide quem pode cada transição. */
@RestController
@RequestMapping("/orders/{id}")
public class OrderLifecycleController {

  private final PickupService pickupService;
  private final OrderCancellationService cancellationService;
  private final WithdrawalService withdrawalService;
  private final CurrentUserHolder currentUserHolder;

  public OrderLifecycleController(
      PickupService pickupService,
      OrderCancellationService cancellationService,
      WithdrawalService withdrawalService,
      CurrentUserHolder currentUserHolder) {
    this.pickupService = pickupService;
    this.cancellationService = cancellationService;
    this.withdrawalService = withdrawalService;
    this.currentUserHolder = currentUserHolder;
  }

  /** RF-26.3 */
  @PostMapping("/pickup/arrival")
  public OrderLifecycleResponse arrive(
      @PathVariable UUID id, @Valid @RequestBody PickupArrivalRequest request) {
    return pickupService.registrarChegada(courier(), id, request);
  }

  /** RF-26.7 */
  @PostMapping("/pickup/confirmation")
  public OrderLifecycleResponse confirmPickup(@PathVariable UUID id) {
    return pickupService.confirmarColeta(merchant(), id);
  }

  /** RF-26.10 */
  @PostMapping("/pickup/reminders")
  public OrderLifecycleResponse remind(@PathVariable UUID id) {
    return pickupService.pedirNovoAviso(courier(), id);
  }

  /** RF-26.14 */
  @PostMapping("/cancellation")
  public OrderLifecycleResponse cancel(
      @PathVariable UUID id, @Valid @RequestBody CancellationRequest request) {
    return cancellationService.cancelar(merchant(), id, request);
  }

  /** RF-26.22 */
  @PostMapping("/withdrawal")
  public OrderLifecycleResponse withdraw(
      @PathVariable UUID id, @Valid @RequestBody WithdrawalRequest request) {
    return withdrawalService.desistir(courier(), id, request);
  }

  private UUID courier() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException("COURIER_ONLY", "Esta ação é do entregador.");
    }
    return user.userId();
  }

  private UUID merchant() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException("MERCHANT_ONLY", "Esta ação é do estabelecimento.");
    }
    return user.userId();
  }
}
