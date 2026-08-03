package com.uaiou.orders.service;

import com.uaiou.credits.config.CreditsProperties;
import com.uaiou.credits.service.CreditWalletService;
import com.uaiou.orders.Distances;
import com.uaiou.orders.OrderPublishedEvent;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationResponse;
import com.uaiou.orders.dto.OrderListResponse;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.orders.dto.OrderSummary;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.shared.pagination.Paginator;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Estabelecimento;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.EstabelecimentoRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-11.1 a RF-11.9 — criação/publicação de pedido e leitura com escopo pelo papel. */
@Service
public class OrderService {

  private static final List<OrderStatus> STATUS_DO_ENTREGADOR =
      List.of(OrderStatus.ACCEPTED, OrderStatus.FINALIZED, OrderStatus.CONTESTABLE_FINALIZED);

  private final PedidoRepository pedidoRepository;
  private final EstabelecimentoRepository estabelecimentoRepository;
  private final EntregadorRepository entregadorRepository;
  private final UsuarioRepository usuarioRepository;
  private final GeocodingService geocodingService;
  private final CreditWalletService creditWalletService;
  private final CreditsProperties creditsProperties;
  private final OrderEligibilityService eligibilityService;
  private final ApplicationEventPublisher events;

  public OrderService(
      PedidoRepository pedidoRepository,
      EstabelecimentoRepository estabelecimentoRepository,
      EntregadorRepository entregadorRepository,
      UsuarioRepository usuarioRepository,
      GeocodingService geocodingService,
      CreditWalletService creditWalletService,
      CreditsProperties creditsProperties,
      OrderEligibilityService eligibilityService,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.estabelecimentoRepository = estabelecimentoRepository;
    this.entregadorRepository = entregadorRepository;
    this.usuarioRepository = usuarioRepository;
    this.geocodingService = geocodingService;
    this.creditWalletService = creditWalletService;
    this.creditsProperties = creditsProperties;
    this.eligibilityService = eligibilityService;
    this.events = events;
  }

  /**
   * RF-11.1/RF-11.2/RF-11.3/RF-11.4 — validações da mais barata para a mais cara, para não consumir
   * crédito à toa: conta ativa → geocodificação → crédito.
   *
   * <p><strong>Na v1 não há verificação de saldo em dinheiro</strong> (RF-11.1 e escopo-v1.md): o
   * `INSUFFICIENT_BALANCE` que api/pedidos.md descreve pertence ao produto completo, onde existe
   * `saldo` — tabela que a v1 cortou.
   *
   * <p>Tudo numa transação só: se o crédito faltar, o {@code INSERT pedido} reverte junto e nenhum
   * pedido é criado (critério de aceite 2).
   */
  @Transactional
  public OrderResponse create(UUID estabelecimentoId, CreateOrderRequest request) {
    Usuario usuario = requireUsuario(estabelecimentoId);
    if (usuario.getStatus() != UserStatus.ACTIVE) {
      throw new ForbiddenException(
          "ACCOUNT_NOT_ACTIVE", "Só uma conta ativa pode publicar pedidos.");
    }
    if (!request.proposedFee().isPositive()) {
      throw new BadRequestException("INVALID_FIELD", "\"proposedFee\" precisa ser maior que zero.");
    }

    GeocodingService.Coordinates destino = geocodingService.resolve(request.destination());

    // RF-11.4: o lock na linha do estabelecimento serializa a numeração por loja. Precisa vir antes
    // da leitura do maior número, senão dois pedidos simultâneos leem o mesmo valor.
    Estabelecimento estabelecimento =
        estabelecimentoRepository
            .findByIdForUpdate(estabelecimentoId)
            .orElseThrow(
                () ->
                    new NotFoundException("MERCHANT_NOT_FOUND", "Estabelecimento não encontrado."));

    String numero = proximoNumero(estabelecimentoId);
    int custo = creditsProperties.costPerOrder();

    Pedido novo =
        new Pedido(
            UuidV7.next(),
            numero,
            estabelecimentoId,
            request.proposedFee(),
            custo,
            new Pedido.Destino(
                request.destination().district(),
                request.destination().street(),
                request.destination().number(),
                request.destination().complement(),
                destino.lat(),
                destino.longitude()),
            new Pedido.Recebedor(request.receiver().name(), request.receiver().phone()),
            request.expectedDeliveryAt());

    // Trabalhar com a instância DEVOLVIDA por saveAndFlush, não com a que foi construída aqui: o id
    // é atribuído pela aplicação (UuidV7), então Spring Data considera a entidade "não nova" e
    // salva
    // por merge() — que devolve uma cópia gerenciada e deixa o objeto original DESTACADO. Mutar o
    // original depois disso (o publicar() abaixo) não seria visto pelo dirty check, e o pedido
    // ficaria
    // "criado" no banco enquanto a resposta dizia "published".
    // Flush aqui também é necessário por outro motivo: transacao_credito tem FK para pedido_id.
    Pedido pedido = pedidoRepository.saveAndFlush(novo);

    creditWalletService.consumirParaPedido(estabelecimentoId, custo, pedido.getId());

    pedido.publicar();

    // Só depois do commit (RF-11.10) — ver OrderPublishedFanout.
    events.publishEvent(new OrderPublishedEvent(pedido.getId()));

    return toResponse(pedido, estabelecimento, null);
  }

  /**
   * RF-11.6 — o escopo vem do papel, nunca de parâmetro do cliente: MERCHANT vê os próprios,
   * COURIER vê a vitrine elegível ou os seus atribuídos. Deixar o cliente escolher abriria
   * vazamento por manipulação de query.
   */
  @Transactional(readOnly = true)
  public OrderListResponse listForMerchant(
      UUID estabelecimentoId, PagingRequest paging, String baseUri) {
    Page<Pedido> page =
        pedidoRepository.findByEstabelecimentoIdOrderByCriadoEmDesc(
            estabelecimentoId, PageRequest.of(paging.page() - 1, paging.perPage()));
    Estabelecimento estabelecimento =
        estabelecimentoRepository.findById(estabelecimentoId).orElse(null);

    List<OrderSummary> content =
        page.getContent().stream().map(pedido -> toSummary(pedido, estabelecimento, null)).toList();
    return OrderListResponse.de(
        Paginator.paginate(content, page.getTotalElements(), paging, baseUri), null);
  }

  /**
   * RF-11.6/RF-11.7/RF-11.8 — vitrine do entregador. Posição velha (ou conta/disponibilidade fora
   * de ordem) devolve lista vazia com {@code LOCATION_STALE}, nunca um resultado calculado sobre
   * posição obsoleta.
   */
  @Transactional(readOnly = true)
  public OrderListResponse listPublishedForCourier(
      UUID entregadorId, PagingRequest paging, String baseUri) {
    var elegiveis = eligibilityService.pedidosVisiveisPara(entregadorId);
    if (elegiveis.isEmpty()) {
      return OrderListResponse.de(
          Paginator.paginate(List.of(), 0, paging, baseUri), "LOCATION_STALE");
    }

    List<OrderEligibilityService.PedidoElegivel> todos = elegiveis.get();
    int from = Math.min(paging.offset(), todos.size());
    int to = Math.min(from + paging.perPage(), todos.size());

    List<OrderSummary> content = new ArrayList<>();
    for (OrderEligibilityService.PedidoElegivel item : todos.subList(from, to)) {
      Estabelecimento merchant =
          estabelecimentoRepository.findById(item.pedido().getEstabelecimentoId()).orElse(null);
      content.add(toSummary(item.pedido(), merchant, Distances.arredondarKm(item.distanciaKm())));
    }
    return OrderListResponse.de(Paginator.paginate(content, todos.size(), paging, baseUri), null);
  }

  @Transactional(readOnly = true)
  public OrderListResponse listAssignedToCourier(
      UUID entregadorId, PagingRequest paging, String baseUri) {
    Page<Pedido> page =
        pedidoRepository.findByEntregadorIdAndStatusInOrderByCriadoEmDesc(
            entregadorId,
            STATUS_DO_ENTREGADOR,
            PageRequest.of(paging.page() - 1, paging.perPage()));

    List<OrderSummary> content =
        page.getContent().stream()
            .map(
                pedido ->
                    toSummary(
                        pedido,
                        estabelecimentoRepository
                            .findById(pedido.getEstabelecimentoId())
                            .orElse(null),
                        distanciaAte(entregadorId, pedido)))
            .toList();
    return OrderListResponse.de(
        Paginator.paginate(content, page.getTotalElements(), paging, baseUri), null);
  }

  /**
   * RF-11.9 — detalhe para as partes. Pedido de terceiro responde <strong>404, não 403</strong>
   * (critério de aceite 8): 403 confirmaria que o id existe, que é justamente o que não se quer
   * revelar — mesma convenção de api/README.md já usada em T-05/T-06.
   */
  @Transactional(readOnly = true)
  public OrderResponse get(UUID usuarioId, boolean isCourier, UUID pedidoId) {
    Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(this::notFound);

    boolean permitido;
    if (isCourier) {
      permitido = pedido.estaAtribuidoA(usuarioId) || eligibilityService.podeVer(usuarioId, pedido);
    } else {
      permitido = pedido.pertenceAoEstabelecimento(usuarioId);
    }
    if (!permitido) {
      throw notFound();
    }

    Estabelecimento estabelecimento =
        estabelecimentoRepository.findById(pedido.getEstabelecimentoId()).orElse(null);
    return toResponse(pedido, estabelecimento, distanciaAte(isCourier ? usuarioId : null, pedido));
  }

  private String proximoNumero(UUID estabelecimentoId) {
    int proximo = pedidoRepository.maiorNumeroDoEstabelecimento(estabelecimentoId) + 1;
    // 🖼 "Pedido nº 0011" — quatro dígitos com zero à esquerda, crescendo além disso se precisar.
    return String.format("%04d", proximo);
  }

  private BigDecimal distanciaAte(UUID entregadorId, Pedido pedido) {
    if (entregadorId == null) {
      return null;
    }
    Entregador entregador = entregadorRepository.findById(entregadorId).orElse(null);
    if (entregador == null || entregador.getLat() == null || entregador.getLongitude() == null) {
      return null;
    }
    return Distances.arredondarKm(
        Distances.haversineKm(
            entregador.getLat(),
            entregador.getLongitude(),
            pedido.getDestLat(),
            pedido.getDestLong()));
  }

  private OrderResponse toResponse(
      Pedido pedido, Estabelecimento estabelecimento, BigDecimal distanceKm) {
    OrderResponse.CourierRef courier = null;
    if (pedido.getEntregadorId() != null) {
      courier =
          new OrderResponse.CourierRef(
              pedido.getEntregadorId(),
              usuarioRepository
                  .findById(pedido.getEntregadorId())
                  .map(Usuario::getNomeExibicao)
                  .orElse(null));
    }

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/orders/" + pedido.getId()));
    if (pedido.estaNaVitrine()) {
      links.put(
          "counteroffers", LinkRef.get("/api/v1/orders/" + pedido.getId() + "/counteroffers"));
    }

    return new OrderResponse(
        pedido.getId(),
        pedido.getNumero(),
        pedido.getStatus(),
        pedido.getFreteProposto(),
        pedido.getFreteFinal(),
        pedido.getCreditosConsumidos(),
        pedido.getCriadoEm(),
        pedido.getHoraPrevistaEntrega(),
        new DestinationResponse(
            pedido.getDestRua(),
            pedido.getDestNumero(),
            pedido.getDestComplemento(),
            pedido.getDestBairro(),
            pedido.getDestLat(),
            pedido.getDestLong()),
        new OrderResponse.ReceiverResponse(
            pedido.getRecebedorNome(), pedido.getRecebedorTelefone()),
        courier,
        links);
  }

  private OrderSummary toSummary(
      Pedido pedido, Estabelecimento estabelecimento, BigDecimal distanceKm) {
    OrderSummary.MerchantRef merchant = null;
    if (estabelecimento != null) {
      merchant =
          new OrderSummary.MerchantRef(
              estabelecimento.getUsuarioId(),
              estabelecimento.getNomeFantasia(),
              estabelecimento.getLogoObjectKey());
    }

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/orders/" + pedido.getId()));
    if (pedido.estaNaVitrine()) {
      links.put(
          "assignment", new LinkRef("/api/v1/orders/" + pedido.getId() + "/assignment", "POST"));
      links.put(
          "counteroffers",
          new LinkRef("/api/v1/orders/" + pedido.getId() + "/counteroffers", "POST"));
    }

    return new OrderSummary(
        pedido.getId(),
        pedido.getNumero(),
        pedido.getStatus(),
        pedido.getFreteProposto(),
        distanceKm,
        merchant,
        // Vitrine mostra só o bairro: endereço completo é do pedido aceito, não da lista pública.
        DestinationResponse.apenasBairro(pedido.getDestBairro()),
        pedido.getCriadoEm(),
        pedido.getAceitoEm(),
        links);
  }

  private Usuario requireUsuario(UUID usuarioId) {
    return usuarioRepository
        .findById(usuarioId)
        .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Usuário não encontrado."));
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
