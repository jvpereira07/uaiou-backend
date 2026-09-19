package com.uaiou.orders.service;

import com.uaiou.orders.Distances;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.dto.OrderRouteResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.routing.RoutingService;
import com.uaiou.routing.RoutingService.Point;
import com.uaiou.routing.config.RoutingProperties;
import com.uaiou.routing.service.RouteCache;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /orders/{id}/route} — <strong>uma rota só</strong>: de onde o entregador está,
 * passando pelo estabelecimento, até o destino do pedido.
 *
 * <p>Decisão do dono (2026-08-16) que substituiu o desenho de duas pernas independentes de RF-25.4.
 * A consequência aceita está registrada em T-25: como a rota inteira depende da posição de quem
 * pergunta, o trecho loja → destino <strong>não é mais invariante por pedido</strong> e o cache
 * deixa de ser compartilhado entre entregadores — cada posição arredondada distinta é uma chamada
 * ao provedor.
 *
 * <p>Sub-recurso de propósito (RF-25.6): fora do payload de {@code GET /orders}, que é a rota mais
 * quente do serviço e não pode passar a depender de um terceiro para responder.
 */
@Service
public class OrderRouteService {

  private static final Logger log = LoggerFactory.getLogger(OrderRouteService.class);

  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final OrderEligibilityService eligibilityService;
  private final RoutingService routingService;
  private final RouteCache routeCache;
  private final RoutingProperties properties;

  public OrderRouteService(
      PedidoRepository pedidoRepository,
      EntregadorRepository entregadorRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      OrderEligibilityService eligibilityService,
      RoutingService routingService,
      RouteCache routeCache,
      RoutingProperties properties) {
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.eligibilityService = eligibilityService;
    this.routingService = routingService;
    this.routeCache = routeCache;
    this.properties = properties;
  }

  @Transactional(readOnly = true)
  public OrderRouteResponse get(UUID entregadorId, UUID pedidoId) {
    return get(entregadorId, pedidoId, null, null);
  }

  /**
   * @param lat latitude lida agora pelo aparelho; com {@code lng}, tem precedência sobre a posição
   *     salva — é o que faz "recalcular" partir de onde o entregador realmente está.
   */
  @Transactional(readOnly = true)
  public OrderRouteResponse get(UUID entregadorId, UUID pedidoId, BigDecimal lat, BigDecimal lng) {
    Pedido pedido = requirePedidoVisivel(entregadorId, pedidoId);
    Entregador entregador = entregadorRepository.findById(entregadorId).orElseThrow(this::notFound);
    Estabelecimento estabelecimento =
        estabelecimentoRepository.findById(pedido.getEstabelecimentoId()).orElse(null);

    Point origem = coordenadaValida(lat, lng) ? new Point(lat, lng) : pontoDoEntregador(entregador);
    // Depois da coleta a loja já ficou para trás: passar por ela de novo mandaria o entregador
    // voltar ao estabelecimento antes de seguir ao destino.
    Point loja = pedido.getStatus() == OrderStatus.PICKED_UP ? null : pontoDaLoja(estabelecimento);
    Point destino = new Point(pedido.getDestLat(), pedido.getDestLong());

    OrderRouteResponse.Route rota = resolver(pedido.getId(), origem, loja, destino);

    return new OrderRouteResponse(
        pedido.getId(),
        origem == null
            ? null
            : Distances.arredondarKm(
                Distances.haversineKm(origem.lat(), origem.lng(), destino.lat(), destino.lng())),
        rota,
        rota.available() ? properties.attribution() : null,
        links(pedido.getId()));
  }

  /**
   * RF-25.9 — a rota inteira varia com quem pergunta, então a chave carrega a posição do entregador
   * arredondada: quem andou menos que a granularidade configurada reaproveita o trajeto, quem andou
   * mais paga uma chamada nova.
   *
   * <p>RF-25.11 — toda resposta sai registrada com a origem: cache, provedor ou falha.
   */
  private OrderRouteResponse.Route resolver(
      UUID pedidoId, Point origem, Point loja, Point destino) {
    if (origem == null) {
      return OrderRouteResponse.Route.unavailable(
          OrderRouteResponse.Route.COURIER_LOCATION_UNKNOWN);
    }

    // RF-25.5 — a loja pode não ter marcado o ponto (nasceu nulo, V21). A rota continua existindo,
    // direta ao destino, e a resposta diz que ela não passa pela retirada.
    boolean passaPelaLoja = loja != null;
    List<Point> waypoints = new ArrayList<>();
    waypoints.add(origem);
    if (passaPelaLoja) {
      waypoints.add(loja);
    }
    waypoints.add(destino);

    int casas = properties.pickupCoordinateScale();
    String chave =
        "rota:pedido:"
            + pedidoId
            + ":de:"
            + RouteCache.arredondar(origem.lat(), casas)
            + ","
            + RouteCache.arredondar(origem.lng(), casas)
            + (passaPelaLoja ? ":via-loja" : ":direta");

    Optional<RoutingService.Route> emCache = routeCache.find(chave);
    if (emCache.isPresent()) {
      log.info("routing.route outcome=CACHE_HIT viaLoja={}", passaPelaLoja);
      return converter(emCache.get(), passaPelaLoja);
    }

    Optional<RoutingService.Route> doProvedor = routingService.route(waypoints);
    if (doProvedor.isEmpty()) {
      // RF-25.10 — ausência de rota, nunca erro: o pedido segue listável, aceitável e finalizável.
      log.info("routing.route outcome=UNAVAILABLE viaLoja={}", passaPelaLoja);
      return OrderRouteResponse.Route.unavailable(OrderRouteResponse.Route.ROUTING_UNAVAILABLE);
    }

    routeCache.save(chave, doProvedor.get(), properties.pickupCacheTtl());
    log.info("routing.route outcome=PROVIDER_CALL viaLoja={}", passaPelaLoja);
    return converter(doProvedor.get(), passaPelaLoja);
  }

  private OrderRouteResponse.Route converter(RoutingService.Route rota, boolean passaPelaLoja) {
    return new OrderRouteResponse.Route(
        true,
        null,
        passaPelaLoja,
        rota.distanceKm(),
        // Minutos, não segundos: precisão maior seria falsa numa estimativa sem trânsito real.
        (int) Math.round(rota.duration().toSeconds() / 60.0),
        rota.geometry().stream()
            .map(ponto -> new OrderRouteResponse.Coordinate(ponto.lat(), ponto.lng()))
            .toList(),
        rota.steps().stream()
            .map(
                passo ->
                    new OrderRouteResponse.Step(
                        passo.instruction(), passo.distanceMeters(), passo.pointIndex()))
            .toList(),
        // Com loja no caminho a rota tem uma parada intermediária: ela.
        passaPelaLoja && !rota.waypointIndices().isEmpty()
            ? rota.waypointIndices().getFirst()
            : null);
  }

  private Point pontoDoEntregador(Entregador entregador) {
    return temCoordenada(entregador.getLat(), entregador.getLongitude())
        ? new Point(entregador.getLat(), entregador.getLongitude())
        : null;
  }

  private Point pontoDaLoja(Estabelecimento estabelecimento) {
    return estabelecimento != null
            && temCoordenada(estabelecimento.getLat(), estabelecimento.getLongitude())
        ? new Point(estabelecimento.getLat(), estabelecimento.getLongitude())
        : null;
  }

  private boolean temCoordenada(BigDecimal lat, BigDecimal lng) {
    return lat != null && lng != null;
  }

  private boolean coordenadaValida(BigDecimal lat, BigDecimal lng) {
    return temCoordenada(lat, lng)
        && lat.abs().compareTo(BigDecimal.valueOf(90)) <= 0
        && lng.abs().compareTo(BigDecimal.valueOf(180)) <= 0;
  }

  /**
   * Quem pode ver a rota é quem pode agir sobre o pedido: o entregador já atribuído (que vai rodar
   * o trajeto) e o elegível que ainda está decidindo o aceite — que é justamente a decisão que esta
   * task existe para informar.
   */
  private Pedido requirePedidoVisivel(UUID entregadorId, UUID pedidoId) {
    Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(this::notFound);
    if (pedido.estaAtribuidoA(entregadorId) || eligibilityService.podeVer(entregadorId, pedido)) {
      return pedido;
    }
    throw new ForbiddenException(
        "ORDER_NOT_VISIBLE", "Este pedido não está visível para este entregador.");
  }

  private Map<String, LinkRef> links(UUID pedidoId) {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/orders/" + pedidoId + "/route"));
    links.put("order", LinkRef.get("/api/v1/orders/" + pedidoId));
    return links;
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
