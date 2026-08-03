package com.uaiou.orders.service;

import com.uaiou.blocks.service.BloqueioService;
import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.counteroffers.entity.Contraoferta;
import com.uaiou.counteroffers.repository.ContraofertaRepository;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.delivery.service.DeliveryCodeService;
import com.uaiou.orders.OrderAssignedEvent;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.presence.service.CourierPresenceService;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.UserStatus;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-13.1 a RF-13.7 — a transição mais concorrida do sistema. */
@Service
public class AssignmentService {

  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;
  private final UsuarioRepository usuarioRepository;
  private final ContraofertaRepository contraofertaRepository;
  private final OtpRepository otpRepository;
  private final DeliveryCodeService deliveryCodeService;
  private final BloqueioService bloqueioService;
  private final CourierPresenceService courierPresenceService;
  private final ApplicationEventPublisher events;

  public AssignmentService(
      PedidoRepository pedidoRepository,
      EntregadorRepository entregadorRepository,
      UsuarioRepository usuarioRepository,
      ContraofertaRepository contraofertaRepository,
      OtpRepository otpRepository,
      DeliveryCodeService deliveryCodeService,
      BloqueioService bloqueioService,
      CourierPresenceService courierPresenceService,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
    this.usuarioRepository = usuarioRepository;
    this.contraofertaRepository = contraofertaRepository;
    this.otpRepository = otpRepository;
    this.deliveryCodeService = deliveryCodeService;
    this.bloqueioService = bloqueioService;
    this.courierPresenceService = courierPresenceService;
    this.events = events;
  }

  /**
   * RF-13.2 — a transação abre com {@code SELECT ... FOR UPDATE} no pedido. Tudo o que decide o
   * resultado é lido <em>depois</em> do lock (RF-13.3): o estado pode ter mudado entre a listagem e
   * o aceite, e é justamente esse intervalo que a corrida explora.
   *
   * <p>RF-13.5 — idempotência sem tabela de chaves: se o pedido já está atribuído <strong>a este
   * mesmo entregador</strong>, devolve a mesma atribuição em vez de 409. Retry de rede não pode
   * parecer corrida perdida. Essa forma é mais forte que honrar o cabeçalho {@code
   * Idempotency-Key}, porque protege mesmo quando o cliente esquece de enviá-lo — e "aceitar duas
   * vezes" não tem significado próprio que uma chave distinguiria.
   */
  @Transactional
  public AssignmentResponse accept(UUID entregadorId, UUID pedidoId) {
    Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElseThrow(this::notFound);

    if (pedido.estaAtribuidoA(entregadorId)) {
      return toResponse(pedido);
    }
    if (pedido.getStatus() == OrderStatus.ACCEPTED
        || pedido.getStatus() == OrderStatus.FINALIZED
        || pedido.getStatus() == OrderStatus.CONTESTABLE_FINALIZED) {
      throw new ConflictException(
          "ORDER_ALREADY_ASSIGNED", "Este pedido já foi aceito por outro entregador.");
    }
    if (!pedido.estaNaVitrine()) {
      // Cancelado ou em estado que não admite aceite — não revela mais que o necessário.
      throw notFound();
    }

    revalidarEntregador(entregadorId, pedido);

    pedido.aceitarPor(entregadorId);

    // RF-13.4: as pendentes deste pedido perdem o objeto — inclusive a do próprio aceitante.
    List<Contraoferta> pendentes =
        contraofertaRepository.findByPedidoIdAndStatus(pedidoId, CounterofferStatus.PENDING);
    pendentes.forEach(Contraoferta::invalidar);
    List<UUID> proponentes =
        pendentes.stream()
            .map(Contraoferta::getEntregadorId)
            .filter(id -> !id.equals(entregadorId))
            .distinct()
            .toList();

    // Critério de aceite 8: exatamente um código por pedido. A UNIQUE de otp (V7) é a última linha
    // de defesa; o guard evita depender dela num caminho que o lock já serializa.
    if (!otpRepository.existsByPedidoId(pedidoId)) {
      deliveryCodeService.gerarPara(pedidoId);
    }

    events.publishEvent(new OrderAssignedEvent(pedidoId, entregadorId, proponentes));

    return toResponse(pedido);
  }

  /**
   * RF-13.3 — revalidação dentro do lock. Cada porta tem o seu erro porque cada uma diz algo
   * diferente ao app: bloqueado é decisão do estabelecimento, indisponível/inativo é estado do
   * próprio entregador.
   */
  private void revalidarEntregador(UUID entregadorId, Pedido pedido) {
    Entregador entregador =
        entregadorRepository
            .findById(entregadorId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "COURIER_NOT_FOUND", "Perfil de entregador não encontrado."));

    boolean contaAtiva =
        usuarioRepository
            .findById(entregadorId)
            .map(usuario -> usuario.getStatus() == UserStatus.ACTIVE)
            .orElse(false);
    if (!contaAtiva) {
      throw new ForbiddenException(
          "ACCOUNT_NOT_ACTIVE", "Só uma conta ativa pode aceitar pedidos.");
    }
    if (!entregador.isDisponivel() || !courierPresenceService.hasFreshPresence(entregadorId)) {
      throw new ForbiddenException(
          "COURIER_NOT_AVAILABLE",
          "Fique disponível e envie sua posição atual antes de aceitar um pedido.");
    }
    // RF-12.3: pode ter sido bloqueado entre a listagem e o aceite.
    bloqueioService.requireNotBlocked(pedido.getEstabelecimentoId(), entregadorId);
  }

  private AssignmentResponse toResponse(Pedido pedido) {
    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("order", LinkRef.get("/api/v1/orders/" + pedido.getId()));
    links.put("delivery", LinkRef.get("/api/v1/orders/" + pedido.getId() + "/delivery"));

    return new AssignmentResponse(
        pedido.getId(),
        pedido.getEntregadorId(),
        pedido.getFreteFinal(),
        pedido.getAceitoEm(),
        links);
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
