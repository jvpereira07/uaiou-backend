package com.uaiou.orders.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.dto.CounterofferDecisionRequest;
import com.uaiou.orders.dto.CounterofferDecisionResponse;
import com.uaiou.orders.service.CounterofferService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code PUT /counteroffers/{id}/decision} (api/pedidos.md) — fora de {@code /orders} de propósito:
 * a contraoferta é o recurso decidido, o pedido é só um dos efeitos.
 */
@RestController
@RequestMapping("/counteroffers/{id}/decision")
public class CounterofferDecisionController {

  private final CounterofferService counterofferService;
  private final CurrentUserHolder currentUserHolder;

  public CounterofferDecisionController(
      CounterofferService counterofferService, CurrentUserHolder currentUserHolder) {
    this.counterofferService = counterofferService;
    this.currentUserHolder = currentUserHolder;
  }

  @PutMapping
  public CounterofferDecisionResponse decide(
      @PathVariable UUID id, @Valid @RequestBody CounterofferDecisionRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.MERCHANT) {
      throw new ForbiddenException("MERCHANT_ONLY", "Só o estabelecimento decide contraoferta.");
    }
    return counterofferService.decide(user.userId(), id, request.outcome());
  }
}
