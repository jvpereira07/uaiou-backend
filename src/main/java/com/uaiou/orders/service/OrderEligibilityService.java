package com.uaiou.orders.service;

import com.uaiou.blocks.repository.BloqueioRepository;
import com.uaiou.orders.Distances;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.config.OrdersProperties;
import com.uaiou.orders.entity.DesistenciaPedido;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.DesistenciaPedidoRepository;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.presence.CourierPresence;
import com.uaiou.presence.service.CourierPresenceService;
import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-11.5 — motor de elegibilidade, reutilizável nos <strong>dois sentidos</strong>: "que pedidos
 * este entregador vê" (vitrine, RF-11.6) e "que entregadores veem este pedido" (fan-out de
 * publicação, RF-11.10). Um único conjunto de filtros para as duas perguntas, porque divergir aqui
 * significaria notificar quem não consegue ver — ou pior, o contrário.
 *
 * <p>Filtros (RN-01.1): entregador {@code ativo} e {@code disponivel}, posição fresca (T-10),
 * dentro do raio (caixa delimitadora + distância real, T-02) e não bloqueado pelo estabelecimento
 * (T-12).
 */
@Service
public class OrderEligibilityService {

  private static final List<OrderStatus> NA_VITRINE =
      List.of(OrderStatus.PUBLISHED, OrderStatus.IN_NEGOTIATION);

  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;
  private final UsuarioRepository usuarioRepository;
  private final BloqueioRepository bloqueioRepository;
  private final CourierPresenceService courierPresenceService;
  private final OrdersProperties properties;
  private final DesistenciaPedidoRepository desistenciaRepository;

  public OrderEligibilityService(
      PedidoRepository pedidoRepository,
      EntregadorRepository entregadorRepository,
      UsuarioRepository usuarioRepository,
      BloqueioRepository bloqueioRepository,
      CourierPresenceService courierPresenceService,
      OrdersProperties properties,
      DesistenciaPedidoRepository desistenciaRepository) {
    this.desistenciaRepository = desistenciaRepository;
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
    this.usuarioRepository = usuarioRepository;
    this.bloqueioRepository = bloqueioRepository;
    this.courierPresenceService = courierPresenceService;
    this.properties = properties;
  }

  /**
   * Sentido "entregador → pedidos" (RF-11.6). {@link Optional#empty()} significa "este entregador
   * não está elegível a ver nada agora" — inativo, indisponível ou com posição velha (RF-11.8) —, o
   * que o chamador traduz em lista vazia com aviso, não em erro.
   */
  @Transactional(readOnly = true)
  public Optional<List<PedidoElegivel>> pedidosVisiveisPara(UUID entregadorId) {
    Entregador entregador = entregadorRepository.findById(entregadorId).orElse(null);
    if (entregador == null || !entregador.isDisponivel() || !contaAtiva(entregadorId)) {
      return Optional.empty();
    }
    // Frescor pela regra de T-10 (dono da regra), consultada pontualmente — não pela lista global
    // de
    // elegíveis: aqui a pergunta é sobre UM entregador, e a resposta não deve depender do cache.
    if (!courierPresenceService.hasFreshPresence(entregadorId)
        || entregador.getLat() == null
        || entregador.getLongitude() == null) {
      return Optional.empty();
    }

    Distances.BoundingBox caixa =
        Distances.caixaDelimitadora(
            entregador.getLat(), entregador.getLongitude(), properties.eligibilityRadiusKm());

    List<PedidoElegivel> visiveis =
        pedidoRepository
            .findNaCaixaDelimitadora(
                NA_VITRINE,
                caixa.latMin(),
                caixa.latMax(),
                caixa.longMin(),
                caixa.longMax(),
                entregadorId)
            .stream()
            .map(
                pedido ->
                    new PedidoElegivel(
                        pedido,
                        Distances.haversineKm(
                            entregador.getLat(),
                            entregador.getLongitude(),
                            pedido.getDestLat(),
                            pedido.getDestLong())))
            // A caixa é um quadrado; o raio é um círculo. Sem este corte, os cantos entrariam.
            .filter(candidato -> candidato.distanciaKm() <= properties.eligibilityRadiusKm())
            .sorted((a, b) -> Double.compare(a.distanciaKm(), b.distanciaKm()))
            .toList();

    return Optional.of(visiveis);
  }

  /**
   * Sentido "pedido → entregadores" (RF-11.10). Parte da presença já filtrada por
   * disponibilidade+frescor (T-10) e aplica raio, bloqueio e conta ativa.
   */
  @Transactional(readOnly = true)
  public List<UUID> entregadoresElegiveisPara(Pedido pedido) {
    Set<UUID> bloqueados =
        Set.copyOf(bloqueioRepository.findEntregadoresBloqueadosPor(pedido.getEstabelecimentoId()));
    // RF-26.28 — na republicação após desistência, quem desistiu não é avisado de novo.
    Set<UUID> desistiram =
        desistenciaRepository.findByPedidoId(pedido.getId()).stream()
            .map(DesistenciaPedido::getEntregadorId)
            .collect(Collectors.toSet());

    return courierPresenceService.findEligibleCouriers().stream()
        .filter(presenca -> !bloqueados.contains(presenca.courierId()))
        .filter(presenca -> !desistiram.contains(presenca.courierId()))
        .filter(presenca -> presenca.lat() != null && presenca.longitude() != null)
        .filter(
            presenca ->
                Distances.haversineKm(
                        presenca.lat(),
                        presenca.longitude(),
                        pedido.getDestLat(),
                        pedido.getDestLong())
                    <= properties.eligibilityRadiusKm())
        .map(CourierPresence::courierId)
        .filter(this::contaAtiva)
        .toList();
  }

  /** Elegibilidade pontual — usada por T-13/T-14 para revalidar dentro do lock. */
  @Transactional(readOnly = true)
  public boolean podeVer(UUID entregadorId, Pedido pedido) {
    return pedidosVisiveisPara(entregadorId)
        .map(lista -> lista.stream().anyMatch(item -> item.pedido().getId().equals(pedido.getId())))
        .orElse(false);
  }

  private boolean contaAtiva(UUID usuarioId) {
    return usuarioRepository
        .findById(usuarioId)
        .map(usuario -> usuario.getStatus() == UserStatus.ACTIVE)
        .orElse(false);
  }

  /** Pedido elegível com a distância já calculada — evita recalcular para montar a resposta. */
  public record PedidoElegivel(Pedido pedido, double distanciaKm) {}
}
