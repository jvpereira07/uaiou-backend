package com.uaiou.orders.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.dto.CourierRouteResponse;
import com.uaiou.orders.service.MultiOrderRouteService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /couriers/me/route} — trajeto único por todos os pedidos em andamento do entregador.
 * Mesmos {@code lat}/{@code lng} opcionais de {@link OrderRouteController}. {@code withOrder}
 * simula um pedido da vitrine no roteiro, sem aceitá-lo, e devolve quanto ele acrescenta.
 */
@RestController
@RequestMapping("/couriers/me/route")
public class CourierRouteController {

  private final MultiOrderRouteService multiOrderRouteService;
  private final CurrentUserHolder currentUserHolder;

  public CourierRouteController(
      MultiOrderRouteService multiOrderRouteService, CurrentUserHolder currentUserHolder) {
    this.multiOrderRouteService = multiOrderRouteService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public CourierRouteResponse route(
      @RequestParam(required = false) BigDecimal lat,
      @RequestParam(required = false) BigDecimal lng,
      @RequestParam(required = false) UUID withOrder) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException("COURIER_ONLY", "A rota da entrega é do entregador.");
    }
    return multiOrderRouteService.get(user.userId(), lat, lng, withOrder);
  }
}
