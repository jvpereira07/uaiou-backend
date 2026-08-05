package com.uaiou.orders.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.dto.CodeDispatchRequest;
import com.uaiou.orders.dto.CodeDispatchResponse;
import com.uaiou.orders.dto.DeliveryCodeResponse;
import com.uaiou.orders.service.ContingencyService;
import com.uaiou.orders.service.DeliveryCodeReaderService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
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
 * {@code /orders/{id}/delivery/code*} do lado do estabelecimento dono (api/entregas.md) — RF-15.11,
 * RF-16.4. Entregador e admin não têm rota aqui (RN-08.3/RN-08.9).
 */
@RestController
@RequestMapping("/orders/{orderId}/delivery")
public class DeliveryCodeController {

  private final DeliveryCodeReaderService deliveryCodeReaderService;
  private final ContingencyService contingencyService;
  private final CurrentUserHolder currentUserHolder;

  public DeliveryCodeController(
      DeliveryCodeReaderService deliveryCodeReaderService,
      ContingencyService contingencyService,
      CurrentUserHolder currentUserHolder) {
    this.deliveryCodeReaderService = deliveryCodeReaderService;
    this.contingencyService = contingencyService;
    this.currentUserHolder = currentUserHolder;
  }

  /**
   * RF-15.11 — entregador (mesmo atribuído) e admin não veem o código; só o estabelecimento dono.
   */
  @GetMapping("/code")
  public DeliveryCodeResponse readCode(@PathVariable UUID orderId) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() == Role.COURIER) {
      throw new ForbiddenException(
          "CODE_NOT_VISIBLE_TO_COURIER", "O código não é exposto ao entregador (RN-08.3).");
    }
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException(
          "MERCHANT_ONLY", "Esta rota é do estabelecimento dono do pedido.");
    }
    return deliveryCodeReaderService.read(user.userId(), orderId);
  }

  @PostMapping("/code-dispatches")
  @ResponseStatus(HttpStatus.CREATED)
  public CodeDispatchResponse dispatch(
      @PathVariable UUID orderId, @RequestBody(required = false) CodeDispatchRequest request) {
    CodeDispatchRequest body = request == null ? new CodeDispatchRequest(null) : request;
    return contingencyService.despachar(requireMerchant().userId(), orderId, body);
  }

  private AuthenticatedUser requireMerchant() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException(
          "MERCHANT_ONLY", "Esta rota é do estabelecimento dono do pedido.");
    }
    return user;
  }
}
