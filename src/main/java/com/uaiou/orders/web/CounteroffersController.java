package com.uaiou.orders.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.dto.CounterofferResponse;
import com.uaiou.orders.dto.CreateCounterofferRequest;
import com.uaiou.orders.service.CounterofferService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** {@code /orders/{id}/counteroffers} (api/pedidos.md) — RF-14.1/RF-14.4. */
@RestController
@RequestMapping("/orders/{orderId}/counteroffers")
public class CounteroffersController {

  private final CounterofferService counterofferService;
  private final CurrentUserHolder currentUserHolder;

  public CounteroffersController(
      CounterofferService counterofferService, CurrentUserHolder currentUserHolder) {
    this.counterofferService = counterofferService;
    this.currentUserHolder = currentUserHolder;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CounterofferResponse create(
      @PathVariable UUID orderId, @Valid @RequestBody CreateCounterofferRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException("COURIER_ONLY", "Só entregadores propõem contraoferta.");
    }
    return counterofferService.create(user.userId(), orderId, request.proposedFee());
  }

  @GetMapping
  public List<CounterofferResponse> list(@PathVariable UUID orderId) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException(
          "MERCHANT_ONLY", "Só o estabelecimento vê as contraofertas recebidas.");
    }
    return counterofferService.list(user.userId(), orderId);
  }
}
