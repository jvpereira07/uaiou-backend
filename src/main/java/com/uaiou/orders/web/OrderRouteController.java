package com.uaiou.orders.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.orders.dto.OrderRouteResponse;
import com.uaiou.orders.service.OrderRouteService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF-25.6 — {@code GET /orders/{id}/route}, sub-recurso do pedido no mesmo estilo de {@code
 * /delivery} (T-15).
 *
 * <p>Rota do entregador: é ele quem decide se o frete vale a pena e quem percorre o trajeto. O
 * cliente web só publica e acompanha (fora de escopo em T-25).
 */
@RestController
@RequestMapping("/orders/{orderId}/route")
public class OrderRouteController {

  private final OrderRouteService orderRouteService;
  private final CurrentUserHolder currentUserHolder;

  public OrderRouteController(
      OrderRouteService orderRouteService, CurrentUserHolder currentUserHolder) {
    this.orderRouteService = orderRouteService;
    this.currentUserHolder = currentUserHolder;
  }

  /**
   * {@code lat}/{@code lng} opcionais: a leitura de GPS que o app tem <em>agora</em>. Sem eles, a
   * origem é a última posição reportada em {@code PUT /me/location} — que pode estar velha quando o
   * entregador não está disponível e o app parou de enviar.
   */
  @GetMapping
  public OrderRouteResponse route(
      @PathVariable UUID orderId,
      @RequestParam(required = false) BigDecimal lat,
      @RequestParam(required = false) BigDecimal lng) {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException("COURIER_ONLY", "A rota da entrega é do entregador.");
    }
    return orderRouteService.get(user.userId(), orderId, lat, lng);
  }
}
