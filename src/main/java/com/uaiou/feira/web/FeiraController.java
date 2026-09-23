package com.uaiou.feira.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.feira.config.FeiraProperties;
import com.uaiou.feira.dto.FeiraDtos.CapturaResponse;
import com.uaiou.feira.dto.FeiraDtos.EntrarRequest;
import com.uaiou.feira.dto.FeiraDtos.EntrarResponse;
import com.uaiou.feira.dto.FeiraDtos.EstandeResponse;
import com.uaiou.feira.dto.FeiraDtos.PedidoResponse;
import com.uaiou.feira.service.FeiraGuard;
import com.uaiou.feira.service.FeiraOrderService;
import com.uaiou.feira.service.FeiraPlayerService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
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
 * Rotas do jogador no modo feira (docs/feira/). Separadas das rotas do produto de propósito: o
 * desvio da feira entra por aqui, e nenhum serviço do produto ganha um {@code if} por causa dela.
 */
@RestController
@RequestMapping("/feira")
public class FeiraController {

  private final FeiraPlayerService playerService;
  private final FeiraOrderService orderService;
  private final FeiraGuard guard;
  private final FeiraProperties properties;
  private final CurrentUserHolder currentUserHolder;

  public FeiraController(
      FeiraPlayerService playerService,
      FeiraOrderService orderService,
      FeiraGuard guard,
      FeiraProperties properties,
      CurrentUserHolder currentUserHolder) {
    this.playerService = playerService;
    this.orderService = orderService;
    this.guard = guard;
    this.properties = properties;
    this.currentUserHolder = currentUserHolder;
  }

  /**
   * Única rota que responde com a flag desligada, e de propósito: é daqui que app e painel
   * descobrem se o modo feira está ligado. Sem ela cada cliente precisaria de uma cópia da flag no
   * próprio ambiente, e duas cópias de uma flag divergem.
   */
  @GetMapping("/config")
  public Map<String, Boolean> config() {
    return Map.of("enabled", properties.enabled());
  }

  /** Rota pública do modo: é ela que cria a sessão. */
  @PostMapping("/jogadores")
  @ResponseStatus(HttpStatus.CREATED)
  public EntrarResponse entrar(@Valid @RequestBody EntrarRequest request) {
    guard.requireEnabled();
    return playerService.entrar(request);
  }

  /**
   * Onde fica o estande, com a descrição escrita pelo operador. O jogador precisa dela na coleta:
   * num salão o pino sozinho não acha a mesa.
   */
  @GetMapping("/estande")
  public EstandeResponse estande() {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireJogador(user);
    return orderService.estande();
  }

  @GetMapping("/pedidos")
  public List<PedidoResponse> pedidos() {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireJogador(user);
    return orderService.listarParaJogador(user.userId());
  }

  @PostMapping("/pedidos/{pedidoId}/capturas")
  @ResponseStatus(HttpStatus.CREATED)
  public CapturaResponse capturar(@PathVariable UUID pedidoId) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireJogador(user);
    return orderService.capturar(pedidoId, user.userId());
  }

  /**
   * Finalização no ponto (docs/feira/01-fluxos.md). Sem código: quem chega entrega. O pedido aqui é
   * o do jogador (a instância criada na captura), não o modelo publicado pelo admin.
   */
  @PostMapping("/pedidos/{pedidoId}/finalizacao")
  public CapturaResponse finalizar(@PathVariable UUID pedidoId) {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireJogador(user);
    return orderService.finalizar(pedidoId, user.userId());
  }

  @GetMapping("/capturas")
  public List<CapturaResponse> minhasCapturas() {
    AuthenticatedUser user = currentUserHolder.require();
    guard.requireJogador(user);
    return orderService.minhasCapturas(user.userId());
  }
}
