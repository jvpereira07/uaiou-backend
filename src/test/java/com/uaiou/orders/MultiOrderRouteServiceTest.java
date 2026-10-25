package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uaiou.orders.dto.CourierRouteResponse;
import com.uaiou.orders.dto.CourierRouteResponse.Stop;
import com.uaiou.orders.dto.CourierRouteResponse.StopKind;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.orders.service.MultiOrderRouteService;
import com.uaiou.orders.service.OrderEligibilityService;
import com.uaiou.routing.RoutingService;
import com.uaiou.routing.RoutingService.Matrix;
import com.uaiou.routing.RoutingService.Point;
import com.uaiou.routing.RoutingService.Route;
import com.uaiou.routing.config.RoutingProperties;
import com.uaiou.routing.service.RouteCache;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unidade, não integração: o que está em teste é a montagem das paradas e a escolha da ordem — o
 * banco e o provedor são dublês.
 */
class MultiOrderRouteServiceTest {

  private static final UUID ENTREGADOR = UUID.randomUUID();
  private static final UUID LOJA_A = UUID.randomUUID();
  private static final UUID LOJA_B = UUID.randomUUID();

  private final PedidoRepository pedidos = mock(PedidoRepository.class);
  private final EntregadorRepository entregadores = mock(EntregadorRepository.class);
  private final EstabelecimentoRepository lojas = mock(EstabelecimentoRepository.class);
  private final RoutingService routing = mock(RoutingService.class);
  private final RouteCache cache = mock(RouteCache.class);
  private final OrderEligibilityService elegibilidade = mock(OrderEligibilityService.class);

  private MultiOrderRouteService servico;

  @BeforeEach
  void montar() {
    servico =
        new MultiOrderRouteService(
            pedidos,
            entregadores,
            lojas,
            routing,
            cache,
            new RoutingProperties(
                "osrm",
                "",
                "http://osrm:5000",
                "motorcycle",
                "pt",
                Duration.ofSeconds(3),
                Duration.ofDays(7),
                Duration.ofMinutes(5),
                3,
                "© OpenStreetMap contributors"),
            elegibilidade);
    Entregador entregador = mock(Entregador.class);
    when(entregador.getLat()).thenReturn(new BigDecimal("0"));
    when(entregador.getLongitude()).thenReturn(new BigDecimal("0"));
    when(entregadores.findById(ENTREGADOR)).thenReturn(Optional.of(entregador));
    // Fora do thenReturn: criar e configurar um mock dentro do stubbing de outro é
    // UnfinishedStubbing.
    List<Estabelecimento> estabelecimentos = List.of(loja(LOJA_A, "1"), loja(LOJA_B, "3"));
    when(lojas.findAllById(anyList())).thenReturn(estabelecimentos);
    when(cache.find(anyString())).thenReturn(Optional.empty());
  }

  /**
   * Simulação: o pedido da vitrine entra no roteiro sem ser aceito, e o acréscimo é a diferença
   * para o roteiro de hoje — calculado do mesmo ponto, sem o candidato.
   */
  @Test
  void aCandidateOrderIsSimulatedAndTheExtraDistanceIsReported() {
    Pedido atual = pedido(LOJA_A, OrderStatus.ACCEPTED, "2", Instant.parse("2026-10-25T10:00:00Z"));
    Pedido candidato = pedido(LOJA_B, OrderStatus.PUBLISHED, "4", null);
    when(pedidos.findByEntregadorIdAndStatusIn(any(), anyList())).thenReturn(List.of(atual));
    when(pedidos.findById(candidato.getId())).thenReturn(Optional.of(candidato));
    when(elegibilidade.podeVer(ENTREGADOR, candidato)).thenReturn(true);
    when(routing.matrix(anyList())).thenReturn(Optional.empty());
    // 3 pontos = roteiro de hoje (origem, loja A, destino A); 5 = com o candidato.
    when(routing.route(anyList()))
        .thenAnswer(
            chamada -> {
              List<?> pontos = chamada.getArgument(0);
              return Optional.of(
                  pontos.size() == 3
                      ? new Route(
                          new BigDecimal("3.0"),
                          Duration.ofMinutes(6),
                          List.of(p("0"), p("1"), p("2")),
                          List.of(),
                          List.of(1))
                      : new Route(
                          new BigDecimal("7.5"),
                          Duration.ofMinutes(15),
                          List.of(p("0"), p("1"), p("2"), p("3"), p("4")),
                          List.of(),
                          List.of(1, 2, 3)));
            });

    CourierRouteResponse resposta = servico.get(ENTREGADOR, null, null, candidato.getId());

    assertThat(resposta.candidateOrderId()).isEqualTo(candidato.getId());
    assertThat(resposta.addedDistanceKm()).isEqualByComparingTo("4.5");
    assertThat(resposta.addedDurationMinutes()).isEqualTo(9);
    assertThat(resposta.stops()).extracting(Stop::orderId).contains(candidato.getId());
  }

  @Test
  void aCandidateTheCourierCannotSeeIsRefused() {
    Pedido candidato = pedido(LOJA_B, OrderStatus.PUBLISHED, "4", null);
    when(pedidos.findByEntregadorIdAndStatusIn(any(), anyList())).thenReturn(List.of());
    when(pedidos.findById(candidato.getId())).thenReturn(Optional.of(candidato));
    when(elegibilidade.podeVer(ENTREGADOR, candidato)).thenReturn(false);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> servico.get(ENTREGADOR, null, null, candidato.getId()))
        .isInstanceOf(com.uaiou.shared.error.ForbiddenException.class);
    verify(routing, never()).route(anyList());
  }

  @Test
  void noActiveOrdersMeansNoRouteAndNoProviderCall() {
    when(pedidos.findByEntregadorIdAndStatusIn(any(), anyList())).thenReturn(List.of());

    CourierRouteResponse resposta = servico.get(ENTREGADOR, null, null);

    assertThat(resposta.available()).isFalse();
    assertThat(resposta.unavailableReason()).isEqualTo(CourierRouteResponse.NO_ACTIVE_ORDERS);
    verify(routing, never()).route(anyList());
  }

  /**
   * Pontos na matriz: 0 origem, 1 loja A, 2 destino A, 3 loja B, 4 destino B. O destino B fica ao
   * lado da loja B e as duas lojas são vizinhas: a ordem ótima resolve B inteiro e depois A.
   */
  @Test
  void ordersStopsByTheMatrixAndMapsEachToItsPointInTheGeometry() {
    Pedido a = pedido(LOJA_A, OrderStatus.ACCEPTED, "2", Instant.parse("2026-10-25T10:00:00Z"));
    Pedido b = pedido(LOJA_B, OrderStatus.ACCEPTED, "4", Instant.parse("2026-10-25T10:05:00Z"));
    when(pedidos.findByEntregadorIdAndStatusIn(any(), anyList())).thenReturn(List.of(a, b));
    int[][] tempos = {
      {0, 100, 300, 120, 130},
      {100, 0, 200, 30, 40},
      {300, 200, 0, 210, 290},
      {120, 30, 210, 0, 10},
      {130, 40, 290, 10, 0},
    };
    when(routing.matrix(anyList())).thenReturn(Optional.of(new Matrix(tempos, tempos)));
    when(routing.route(anyList()))
        .thenReturn(
            Optional.of(
                new Route(
                    new BigDecimal("4.2"),
                    Duration.ofSeconds(540),
                    List.of(p("0"), p("3"), p("4"), p("1"), p("2")),
                    List.of(),
                    List.of(1, 2, 3))));

    CourierRouteResponse resposta = servico.get(ENTREGADOR, null, null);

    assertThat(resposta.available()).isTrue();
    assertThat(resposta.optimized()).isTrue();
    assertThat(resposta.durationMinutes()).isEqualTo(9);
    assertThat(resposta.stops())
        .extracting(Stop::orderId, Stop::kind, Stop::pointIndex)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(b.getId(), StopKind.PICKUP, 1),
            org.assertj.core.groups.Tuple.tuple(b.getId(), StopKind.DROPOFF, 2),
            org.assertj.core.groups.Tuple.tuple(a.getId(), StopKind.PICKUP, 3),
            org.assertj.core.groups.Tuple.tuple(a.getId(), StopKind.DROPOFF, 4));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Point>> waypoints = ArgumentCaptor.forClass(List.class);
    verify(routing).route(waypoints.capture());
    assertThat(waypoints.getValue())
        .extracting(ponto -> ponto.lat().toPlainString())
        .containsExactly("0", "3", "4", "1", "2");
  }

  @Test
  void withoutAMatrixFallsBackToAcceptanceOrderSkippingCollectedPickups() {
    Pedido a = pedido(LOJA_A, OrderStatus.PICKED_UP, "2", Instant.parse("2026-10-25T10:00:00Z"));
    Pedido b = pedido(LOJA_B, OrderStatus.ACCEPTED, "4", Instant.parse("2026-10-25T10:05:00Z"));
    when(pedidos.findByEntregadorIdAndStatusIn(any(), anyList())).thenReturn(List.of(b, a));
    when(routing.matrix(anyList())).thenReturn(Optional.empty());
    when(routing.route(anyList())).thenReturn(Optional.empty());

    CourierRouteResponse resposta = servico.get(ENTREGADOR, null, null);

    assertThat(resposta.available()).isFalse();
    assertThat(resposta.optimized()).isFalse();
    assertThat(resposta.stops())
        .extracting(Stop::orderId, Stop::kind)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(a.getId(), StopKind.DROPOFF),
            org.assertj.core.groups.Tuple.tuple(b.getId(), StopKind.PICKUP),
            org.assertj.core.groups.Tuple.tuple(b.getId(), StopKind.DROPOFF));
  }

  private Pedido pedido(UUID loja, OrderStatus status, String destLat, Instant aceitoEm) {
    Pedido pedido = mock(Pedido.class);
    when(pedido.getId()).thenReturn(UUID.randomUUID());
    when(pedido.getEstabelecimentoId()).thenReturn(loja);
    when(pedido.getStatus()).thenReturn(status);
    when(pedido.getDestLat()).thenReturn(new BigDecimal(destLat));
    when(pedido.getDestLong()).thenReturn(BigDecimal.ZERO);
    when(pedido.getAceitoEm()).thenReturn(aceitoEm);
    return pedido;
  }

  private Estabelecimento loja(UUID id, String lat) {
    Estabelecimento loja = mock(Estabelecimento.class);
    when(loja.getUsuarioId()).thenReturn(id);
    when(loja.getLat()).thenReturn(new BigDecimal(lat));
    when(loja.getLongitude()).thenReturn(BigDecimal.ZERO);
    return loja;
  }

  private static Point p(String lat) {
    return new Point(new BigDecimal(lat), BigDecimal.ZERO);
  }
}
