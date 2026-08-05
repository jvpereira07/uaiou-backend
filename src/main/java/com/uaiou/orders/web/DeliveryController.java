package com.uaiou.orders.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.dto.CodeRecoveryRequest;
import com.uaiou.orders.dto.CodeRecoveryResponse;
import com.uaiou.orders.dto.DeliveryCompletionRequest;
import com.uaiou.orders.dto.DeliveryCompletionResponse;
import com.uaiou.orders.dto.DeliveryStateResponse;
import com.uaiou.orders.service.ContingencyService;
import com.uaiou.orders.service.DeliveryCompletionService;
import com.uaiou.orders.service.DeliveryStateService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /orders/{id}/delivery/*} do lado do entregador atribuído (api/entregas.md) — RF-15.3,
 * RF-15.6, RF-16.1.
 */
@RestController
@RequestMapping("/orders/{orderId}/delivery")
public class DeliveryController {

  private final DeliveryStateService deliveryStateService;
  private final DeliveryCompletionService deliveryCompletionService;
  private final ContingencyService contingencyService;
  private final CurrentUserHolder currentUserHolder;

  public DeliveryController(
      DeliveryStateService deliveryStateService,
      DeliveryCompletionService deliveryCompletionService,
      ContingencyService contingencyService,
      CurrentUserHolder currentUserHolder) {
    this.deliveryStateService = deliveryStateService;
    this.deliveryCompletionService = deliveryCompletionService;
    this.contingencyService = contingencyService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public DeliveryStateResponse state(@PathVariable UUID orderId) {
    return deliveryStateService.get(requireCourier().userId(), orderId);
  }

  @PostMapping("/completion")
  @ResponseStatus(HttpStatus.CREATED)
  public DeliveryCompletionResponse complete(
      @PathVariable UUID orderId, @Valid @RequestBody DeliveryCompletionRequest request) {
    return deliveryCompletionService.completar(requireCourier().userId(), orderId, request);
  }

  @PostMapping("/code-recoveries")
  @ResponseStatus(HttpStatus.CREATED)
  public CodeRecoveryResponse recover(
      @PathVariable UUID orderId, @RequestBody(required = false) CodeRecoveryRequest request) {
    CodeRecoveryRequest body = request == null ? new CodeRecoveryRequest(null) : request;
    return contingencyService.acionar(requireCourier().userId(), orderId, body);
  }

  private AuthenticatedUser requireCourier() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException(
          "COURIER_ONLY", "Esta rota é do entregador atribuído à entrega.");
    }
    return user;
  }
}
