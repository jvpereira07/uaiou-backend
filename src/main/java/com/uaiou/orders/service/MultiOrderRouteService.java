package com.uaiou.orders.service;

import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.dto.CourierRouteResponse;
import com.uaiou.orders.dto.CourierRouteResponse.Stop;
import com.uaiou.orders.dto.CourierRouteResponse.StopKind;
import com.uaiou.orders.dto.OrderRouteResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.routing.RoutingService;
import com.uaiou.routing.RoutingService.Point;
import com.uaiou.routing.StopSequencer;
import com.uaiou.routing.StopSequencer.Job;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /couriers/me/route} — o trajeto de quem carrega mais de um pedido: da posição do
 * entregador por todas as lojas ainda não visitadas e todos os destinos, numa ordem só.
 *
 * <p>A ordem sai do {@link StopSequencer} sobre a matriz de tempos do provedor; sem matriz
 * (Geoapify, provedor fora do ar) ou com pedidos demais para a busca exata, cai na ordem de aceite
 * — cada coleta antes da sua entrega. Nos dois casos a resposta diz qual foi ({@code optimized}).
 *
 * <p>Complementa, não substitui, {@code GET /orders/{id}/route}: aquele continua sendo a rota de
 * decisão do aceite, de um pedido só.
 */
@Service
public class MultiOrderRouteService {

  private static final Logger log = LoggerFactory.getLogger(MultiOrderRouteService.class);

  private static final List<OrderStatus> EM_ANDAMENTO =
      List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP);

  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final RoutingService routingService;
  private final RouteCache routeCache;
  private final RoutingProperties properties;
  private final OrderEligibilityService eligibilityService;

  public MultiOrderRouteService(
      PedidoRepository pedidoRepository,
      EntregadorRepository entregadorRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      RoutingService routingService,
      RouteCache routeCache,
      RoutingProperties properties,
      OrderEligibilityService eligibilityService) {
    this.eligibilityService = eligibilityService;
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.routingService = routingService;
    this.routeCache = routeCache;
    this.properties = properties;
  }

  /** Uma parada candidata, com a posição dela na lista de pontos enviada ao provedor. */
  private record Parada(UUID pedidoId, StopKind tipo, Point ponto) {}

  @Transactional(readOnly = true)
  public CourierRouteResponse get(UUID entregadorId, BigDecimal lat, BigDecimal lng) {
    return get(entregadorId, lat, lng, null);
  }

  /**
   * @param candidatoId pedido da vitrine a simular no roteiro, sem aceitá-lo — "como fica o meu
   *     percurso se eu pegar este também?". A resposta traz o acréscimo em relação ao roteiro de
   *     hoje. Só vale para pedido que o entregador pode ver (mesma regra do aceite); nada é
   *     gravado.
   */
  @Transactional(readOnly = true)
  public CourierRouteResponse get(
      UUID entregadorId, BigDecimal lat, BigDecimal lng, UUID candidatoId) {
    Entregador entregador = entregadorRepository.findById(entregadorId).orElseThrow(this::notFound);
    List<Pedido> atuais =
        pedidoRepository.findByEntregadorIdAndStatusIn(entregadorId, EM_ANDAMENTO).stream()
            .sorted(
                Comparator.comparing(
                    Pedido::getAceitoEm, Comparator.nullsLast(Comparator.<Instant>naturalOrder())))
            .toList();

    Pedido candidato = candidato(entregadorId, candidatoId, atuais);
    List<Pedido> pedidos = new ArrayList<>(atuais);
    if (candidato != null) {
      // Por último: na ordem de aceite (o recurso sem matriz), quem ainda não foi aceito vem
      // depois.
      pedidos.add(candidato);
    }
    if (pedidos.isEmpty()) {
      return indisponivel(CourierRouteResponse.NO_ACTIVE_ORDERS, false, List.of(), null);
    }

    Map<UUID, Estabelecimento> lojas =
        estabelecimentoRepository
            .findAllById(pedidos.stream().map(Pedido::getEstabelecimentoId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(Estabelecimento::getUsuarioId, Function.identity()));
    UUID idCandidato = candidato == null ? null : candidato.getId();

    Point origem = coordenadaValida(lat, lng) ? new Point(lat, lng) : pontoDoEntregador(entregador);
    if (origem == null) {
      return indisponivel(
          OrderRouteResponse.Route.COURIER_LOCATION_UNKNOWN,
          false,
          paradas(paradasDe(pedidos, lojas).paradas(), null),
          idCandidato);
    }

    Calculo com = calcular(entregadorId, origem, pedidos, lojas);
    if (com.rota().isEmpty()) {
      return indisponivel(
          OrderRouteResponse.Route.ROUTING_UNAVAILABLE,
          com.otimizada(),
          paradas(com.ordem(), null),
          idCandidato);
    }
    RoutingService.Route trajeto = com.rota().get();

    // O acréscimo compara com o roteiro de hoje, calculado do mesmo ponto. Sem entrega em andamento
    // o roteiro de hoje é vazio e o acréscimo é o trajeto inteiro.
    BigDecimal kmAMais = null;
    Integer minAMais = null;
    if (candidato != null) {
      Optional<RoutingService.Route> hoje =
          atuais.isEmpty()
              ? Optional.of(
                  new RoutingService.Route(BigDecimal.ZERO, Duration.ZERO, List.of(), List.of()))
              : calcular(entregadorId, origem, atuais, lojas).rota();
      if (hoje.isPresent()) {
        kmAMais = trajeto.distanceKm().subtract(hoje.get().distanceKm()).max(BigDecimal.ZERO);
        minAMais = Math.max(0, minutos(trajeto) - minutos(hoje.get()));
      }
    }

    return new CourierRouteResponse(
        true,
        null,
        com.otimizada(),
        trajeto.distanceKm(),
        minutos(trajeto),
        trajeto.geometry().stream()
            .map(ponto -> new OrderRouteResponse.Coordinate(ponto.lat(), ponto.lng()))
            .toList(),
        trajeto.steps().stream()
            .map(
                passo ->
                    new OrderRouteResponse.Step(
                        passo.instruction(), passo.distanceMeters(), passo.pointIndex()))
            .toList(),
        paradas(com.ordem(), indicesDasParadas(trajeto, com.ordem().size())),
        idCandidato,
        kmAMais,
        minAMais,
        properties.attribution(),
        links());
  }

  /**
   * O pedido a simular, validado. Já atribuído a este entregador ele já está no roteiro — o
   * parâmetro vira no-op em vez de erro, para o app não precisar saber disso antes de perguntar.
   */
  private Pedido candidato(UUID entregadorId, UUID candidatoId, List<Pedido> atuais) {
    if (candidatoId == null || atuais.stream().anyMatch(p -> p.getId().equals(candidatoId))) {
      return null;
    }
    Pedido pedido =
        pedidoRepository
            .findById(candidatoId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado."));
    if (!eligibilityService.podeVer(entregadorId, pedido)) {
      throw new ForbiddenException(
          "ORDER_NOT_VISIBLE", "Este pedido não está visível para este entregador.");
    }
    return pedido;
  }

  private record Candidatas(List<Parada> paradas, List<Job> jobs) {}

  /**
   * Índice 0 é a origem; depois, por pedido, a loja (se ainda falta coletar e ela tem ponto) e o
   * destino. A mesma lista serve à matriz e ao Job do sequenciador.
   */
  private Candidatas paradasDe(List<Pedido> pedidos, Map<UUID, Estabelecimento> lojas) {
    List<Parada> candidatas = new ArrayList<>();
    List<Job> jobs = new ArrayList<>();
    for (Pedido pedido : pedidos) {
      Integer coleta = null;
      Point loja =
          pedido.getStatus() == OrderStatus.PICKED_UP
              ? null
              : pontoDaLoja(lojas.get(pedido.getEstabelecimentoId()));
      if (loja != null) {
        candidatas.add(new Parada(pedido.getId(), StopKind.PICKUP, loja));
        coleta = candidatas.size();
      }
      candidatas.add(
          new Parada(
              pedido.getId(),
              StopKind.DROPOFF,
              new Point(pedido.getDestLat(), pedido.getDestLong())));
      jobs.add(new Job(coleta, candidatas.size()));
    }
    return new Candidatas(candidatas, jobs);
  }

  private record Calculo(
      List<Parada> ordem, boolean otimizada, Optional<RoutingService.Route> rota) {}

  private Calculo calcular(
      UUID entregadorId, Point origem, List<Pedido> pedidos, Map<UUID, Estabelecimento> lojas) {
    Candidatas candidatas = paradasDe(pedidos, lojas);
    List<Point> pontos = new ArrayList<>();
    pontos.add(origem);
    candidatas.paradas().forEach(parada -> pontos.add(parada.ponto()));

    Optional<List<Integer>> otima = ordenar(pontos, candidatas.jobs());
    List<Parada> ordem =
        otima
            .map(indices -> indices.stream().map(i -> candidatas.paradas().get(i - 1)).toList())
            .orElse(candidatas.paradas());

    List<Point> waypoints = new ArrayList<>();
    waypoints.add(origem);
    ordem.forEach(parada -> waypoints.add(parada.ponto()));
    return new Calculo(ordem, otima.isPresent(), resolver(entregadorId, ordem, waypoints));
  }

  /** Minutos, não segundos: precisão maior seria falsa numa estimativa sem trânsito real. */
  private int minutos(RoutingService.Route rota) {
    return (int) Math.round(rota.duration().toSeconds() / 60.0);
  }

  /** Um pedido só não tem o que ordenar — e não vale uma chamada de matriz. */
  private Optional<List<Integer>> ordenar(List<Point> pontos, List<Job> jobs) {
    if (jobs.size() < 2 || jobs.size() > StopSequencer.MAX_JOBS) {
      return Optional.empty();
    }
    return routingService.matrix(pontos).flatMap(matriz -> StopSequencer.sequence(matriz, jobs));
  }

  /**
   * RF-25.9 — mesma disciplina de {@link OrderRouteService}: a chave carrega a posição arredondada
   * e a sequência exata de paradas, então coletar um pedido (a loja sai do roteiro) gera chave
   * nova.
   */
  private Optional<RoutingService.Route> resolver(
      UUID entregadorId, List<Parada> ordem, List<Point> waypoints) {
    int casas = properties.pickupCoordinateScale();
    Point origem = waypoints.getFirst();
    String chave =
        "rota:entregador:"
            + entregadorId
            + ":de:"
            + RouteCache.arredondar(origem.lat(), casas)
            + ","
            + RouteCache.arredondar(origem.lng(), casas)
            + ":"
            + ordem.stream()
                .map(parada -> parada.pedidoId() + (parada.tipo() == StopKind.PICKUP ? "P" : "D"))
                .collect(Collectors.joining(","));

    Optional<RoutingService.Route> emCache = routeCache.find(chave);
    if (emCache.isPresent()) {
      log.info("routing.courierRoute outcome=CACHE_HIT stops={}", ordem.size());
      return emCache;
    }
    Optional<RoutingService.Route> doProvedor = routingService.route(waypoints);
    if (doProvedor.isEmpty()) {
      log.info("routing.courierRoute outcome=UNAVAILABLE stops={}", ordem.size());
      return Optional.empty();
    }
    routeCache.save(chave, doProvedor.get(), properties.pickupCacheTtl());
    log.info("routing.courierRoute outcome=PROVIDER_CALL stops={}", ordem.size());
    return doProvedor;
  }

  /**
   * Paradas intermediárias vêm de {@code waypointIndices}; a última é o fim da geometria. Se o
   * provedor não separou as pernas, nenhuma parada ganha índice — melhor nenhum que um errado.
   */
  private List<Integer> indicesDasParadas(RoutingService.Route rota, int quantidade) {
    if (quantidade == 1) {
      return List.of(rota.geometry().size() - 1);
    }
    if (rota.waypointIndices().size() != quantidade - 1) {
      return null;
    }
    List<Integer> indices = new ArrayList<>(rota.waypointIndices());
    indices.add(rota.geometry().size() - 1);
    return indices;
  }

  private List<Stop> paradas(List<Parada> ordem, List<Integer> indices) {
    List<Stop> paradas = new ArrayList<>(ordem.size());
    for (int i = 0; i < ordem.size(); i++) {
      Parada parada = ordem.get(i);
      paradas.add(
          new Stop(
              parada.pedidoId(),
              parada.tipo(),
              new OrderRouteResponse.Coordinate(parada.ponto().lat(), parada.ponto().lng()),
              indices == null ? null : indices.get(i)));
    }
    return List.copyOf(paradas);
  }

  private CourierRouteResponse indisponivel(
      String motivo, boolean otimizada, List<Stop> paradas, UUID candidatoId) {
    return new CourierRouteResponse(
        false,
        motivo,
        otimizada,
        null,
        null,
        null,
        null,
        paradas,
        candidatoId,
        null,
        null,
        null,
        links());
  }

  private Point pontoDoEntregador(Entregador entregador) {
    return entregador.getLat() != null && entregador.getLongitude() != null
        ? new Point(entregador.getLat(), entregador.getLongitude())
        : null;
  }

  private Point pontoDaLoja(Estabelecimento estabelecimento) {
    return estabelecimento != null
            && estabelecimento.getLat() != null
            && estabelecimento.getLongitude() != null
        ? new Point(estabelecimento.getLat(), estabelecimento.getLongitude())
        : null;
  }

  private boolean coordenadaValida(BigDecimal lat, BigDecimal lng) {
    return lat != null
        && lng != null
        && lat.abs().compareTo(BigDecimal.valueOf(90)) <= 0
        && lng.abs().compareTo(BigDecimal.valueOf(180)) <= 0;
  }

  private Map<String, LinkRef> links() {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/couriers/me/route"));
    return links;
  }

  private NotFoundException notFound() {
    return new NotFoundException("COURIER_NOT_FOUND", "Entregador não encontrado.");
  }
}
