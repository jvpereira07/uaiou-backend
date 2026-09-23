package com.uaiou.feira.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.feira.dto.FeiraDtos.CapturaAdminResponse;
import com.uaiou.feira.dto.FeiraDtos.CriarPedidoRequest;
import com.uaiou.feira.dto.FeiraDtos.EstandeRequest;
import com.uaiou.feira.dto.FeiraDtos.EstandeResponse;
import com.uaiou.feira.dto.FeiraDtos.PedidoAdminResponse;
import com.uaiou.feira.service.FeiraGuard;
import com.uaiou.feira.service.FeiraOrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operação do estande: publicar pedido, acompanhar capturas, entregar o prêmio, encerrar. */
@RestController
@RequestMapping("/admin/feira")
public class FeiraAdminController {

  private final FeiraOrderService orderService;
  private final FeiraGuard guard;
  private final CurrentUserHolder currentUserHolder;

  public FeiraAdminController(
      FeiraOrderService orderService, FeiraGuard guard, CurrentUserHolder currentUserHolder) {
    this.orderService = orderService;
    this.guard = guard;
    this.currentUserHolder = currentUserHolder;
  }

  /** Onde o estande está montado — origem da coleta e do mapa no app. */
  @GetMapping("/estande")
  public EstandeResponse estande() {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    return orderService.estande();
  }

  @PutMapping("/estande")
  public EstandeResponse moverEstande(@Valid @RequestBody EstandeRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    return orderService.moverEstande(request);
  }

  @PostMapping("/pedidos")
  @ResponseStatus(HttpStatus.CREATED)
  public PedidoAdminResponse publicar(@Valid @RequestBody CriarPedidoRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    return orderService.publicar(request);
  }

  @GetMapping("/pedidos")
  public List<PedidoAdminResponse> pedidos() {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    return orderService.listarParaAdmin();
  }

  @GetMapping("/pedidos/{pedidoId}/capturas")
  public List<CapturaAdminResponse> capturas(@PathVariable UUID pedidoId) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    return orderService.capturasDoPedido(pedidoId);
  }

  /**
   * Coleta no estande: o jogador aceitou o pedido e veio buscar. É este clique que o libera para ir
   * até o ponto de entrega — o equivalente ao estabelecimento confirmando a coleta no produto.
   */
  @PostMapping("/capturas/{capturaId}/coleta")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmarColeta(@PathVariable UUID capturaId) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    orderService.confirmarColeta(capturaId);
  }

  /** Balcão: o visitante entregou no ponto e voltou; o operador confirma que passou o Bis. */
  @PostMapping("/capturas/{capturaId}/entrega")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void entregar(@PathVariable UUID capturaId) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    orderService.marcarEntregue(capturaId);
  }

  /** Tira a captura do balcão (teste, jogador que sumiu) e cancela o pedido dele se ainda anda. */
  @PostMapping("/capturas/{capturaId}/descarte")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void descartar(@PathVariable UUID capturaId) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    orderService.descartar(capturaId);
  }

  @PostMapping("/pedidos/{pedidoId}/encerramento")
  public PedidoAdminResponse encerrar(@PathVariable UUID pedidoId) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireAdmin(user);
    return orderService.encerrar(pedidoId);
  }
}
