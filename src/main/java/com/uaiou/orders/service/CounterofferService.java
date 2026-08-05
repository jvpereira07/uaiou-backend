package com.uaiou.orders.service;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.counteroffers.entity.Contraoferta;
import com.uaiou.counteroffers.repository.ContraofertaRepository;
import com.uaiou.delivery.repository.OtpRepository;
import com.uaiou.delivery.service.DeliveryCodeService;
import com.uaiou.orders.CounterofferCreatedEvent;
import com.uaiou.orders.CounterofferDecidedEvent;
import com.uaiou.orders.dto.CounterofferDecisionRequest;
import com.uaiou.orders.dto.CounterofferDecisionResponse;
import com.uaiou.orders.dto.CounterofferResponse;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.EntregadorRepository;
import com.uaiou.users.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-14.1 a RF-14.9 — negociação de frete: propor, listar e decidir. */
@Service
public class CounterofferService {

  private final PedidoRepository pedidoRepository;
  private final ContraofertaRepository contraofertaRepository;
  private final EntregadorRepository entregadorRepository;
  private final UsuarioRepository usuarioRepository;
  private final OtpRepository otpRepository;
  private final DeliveryCodeService deliveryCodeService;
  private final CourierEligibilityGuard eligibilityGuard;
  private final ApplicationEventPublisher events;
  private final EntityManager entityManager;

  public CounterofferService(
      PedidoRepository pedidoRepository,
      ContraofertaRepository contraofertaRepository,
      EntregadorRepository entregadorRepository,
      UsuarioRepository usuarioRepository,
      OtpRepository otpRepository,
      DeliveryCodeService deliveryCodeService,
      CourierEligibilityGuard eligibilityGuard,
      ApplicationEventPublisher events,
      EntityManager entityManager) {
    this.pedidoRepository = pedidoRepository;
    this.contraofertaRepository = contraofertaRepository;
    this.entregadorRepository = entregadorRepository;
    this.usuarioRepository = usuarioRepository;
    this.otpRepository = otpRepository;
    this.deliveryCodeService = deliveryCodeService;
    this.eligibilityGuard = eligibilityGuard;
    this.events = events;
    this.entityManager = entityManager;
  }

  /**
   * RF-14.1/RF-14.2 — não precisa do lock do pedido: duas propostas de entregadores DIFERENTES
   * coexistem (RF-14.3, negociar não reserva); o que não pode coexistir é duas do MESMO par
   * pedido+entregador, e isso a UNIQUE parcial de V5 garante como defesa final.
   */
  @Transactional
  public CounterofferResponse create(UUID entregadorId, UUID pedidoId, Money valorProposto) {
    Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(this::notFound);
    if (!pedido.estaNaVitrine()) {
      throw notFound();
    }

    eligibilityGuard.ensureCanTransact(entregadorId, pedido);

    if (contraofertaRepository.existsByPedidoIdAndEntregadorIdAndStatus(
        pedidoId, entregadorId, CounterofferStatus.PENDING)) {
      throw new ConflictException(
          "COUNTEROFFER_ALREADY_PENDING",
          "Você já tem uma contraoferta pendente para este pedido. RN-02.1: uma rodada só.");
    }

    Contraoferta contraoferta =
        new Contraoferta(UuidV7.next(), pedidoId, entregadorId, valorProposto);
    try {
      contraoferta = contraofertaRepository.save(contraoferta);
    } catch (DataIntegrityViolationException e) {
      // Última linha de defesa contra a corrida entre o pré-check acima e este INSERT.
      throw new ConflictException(
          "COUNTEROFFER_ALREADY_PENDING",
          "Você já tem uma contraoferta pendente para este pedido. RN-02.1: uma rodada só.");
    }

    pedido.iniciarNegociacao();

    events.publishEvent(new CounterofferCreatedEvent(pedidoId, contraoferta.getId(), entregadorId));

    return toResponse(contraoferta);
  }

  /** RF-14.4 — score do proponente embutido, para não exigir segunda chamada. */
  @Transactional(readOnly = true)
  public List<CounterofferResponse> list(UUID estabelecimentoId, UUID pedidoId) {
    Pedido pedido =
        pedidoRepository
            .findById(pedidoId)
            .filter(candidato -> candidato.pertenceAoEstabelecimento(estabelecimentoId))
            .orElseThrow(this::notFound);

    return contraofertaRepository.findByPedidoIdOrderByCriadoEmDesc(pedido.getId()).stream()
        .map(this::toResponse)
        .toList();
  }

  /**
   * RF-14.5/RF-14.6/RF-14.7 — um recurso cobre os dois desfechos. RF-14.6/critério 7: mesma
   * disciplina de lock do pedido que T-13 — a corrida entre "aceite direto" e "aceite de
   * contraoferta" também existe aqui, e é o MESMO lock (pedido) que resolve as duas.
   */
  @Transactional
  public CounterofferDecisionResponse decide(
      UUID estabelecimentoId, UUID contraofertaId, CounterofferDecisionRequest.Outcome outcome) {
    Contraoferta contraoferta =
        contraofertaRepository.findById(contraofertaId).orElseThrow(this::notFound);

    Pedido pedido =
        pedidoRepository
            .findByIdForUpdate(contraoferta.getPedidoId())
            .filter(candidato -> candidato.pertenceAoEstabelecimento(estabelecimentoId))
            .orElseThrow(this::notFound);

    // A contraoferta foi carregada ANTES do lock do pedido: se um aceite direto (T-13) ou outra
    // decisão ganhou a corrida e já invalidou/decidiu esta linha, a instância em memória está
    // desatualizada. O refresh força reler do banco agora que o lock garante que nada mais está
    // escrevendo neste pedido — sem ele, o cache de primeiro nível da sessão devolveria a versão
    // stale carregada acima, mesmo que o commit concorrente já tenha mudado a linha.
    entityManager.refresh(contraoferta);
    if (contraoferta.getStatus() != CounterofferStatus.PENDING) {
      throw new ConflictException(
          "COUNTEROFFER_NO_LONGER_VALID",
          "Esta contraoferta já não está mais pendente de decisão.");
    }

    Pedido decidido =
        outcome == CounterofferDecisionRequest.Outcome.ACCEPTED
            ? aceitar(pedido, contraoferta)
            : recusar(pedido, contraoferta);

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("order", LinkRef.get("/api/v1/orders/" + decidido.getId()));
    return new CounterofferDecisionResponse(
        contraoferta.getId(),
        contraoferta.getStatus(),
        contraoferta.getValorProposto(),
        new CounterofferDecisionResponse.OrderRef(
            decidido.getId(), decidido.getStatus(), decidido.getFreteFinal()),
        links);
  }

  private Pedido aceitar(Pedido pedido, Contraoferta contraoferta) {
    contraoferta.aceitar();
    pedido.aceitarPorContraoferta(contraoferta.getEntregadorId(), contraoferta.getValorProposto());

    List<Contraoferta> outrasPendentes =
        contraofertaRepository.findByPedidoIdAndStatus(pedido.getId(), CounterofferStatus.PENDING);
    outrasPendentes.forEach(Contraoferta::invalidar);
    List<UUID> proponentesInvalidados =
        outrasPendentes.stream().map(Contraoferta::getEntregadorId).distinct().toList();

    if (!otpRepository.existsByPedidoId(pedido.getId())) {
      deliveryCodeService.gerarPara(pedido.getId());
    }

    events.publishEvent(
        new CounterofferDecidedEvent(
            pedido.getId(),
            contraoferta.getId(),
            contraoferta.getEntregadorId(),
            CounterofferStatus.ACCEPTED,
            proponentesInvalidados));

    return pedido;
  }

  private Pedido recusar(Pedido pedido, Contraoferta contraoferta) {
    contraoferta.recusar();

    boolean haOutraPendente =
        !contraofertaRepository
            .findByPedidoIdAndStatus(pedido.getId(), CounterofferStatus.PENDING)
            .isEmpty();
    if (!haOutraPendente) {
      pedido.voltarAPublicado();
    }

    events.publishEvent(
        new CounterofferDecidedEvent(
            pedido.getId(),
            contraoferta.getId(),
            contraoferta.getEntregadorId(),
            CounterofferStatus.REJECTED,
            List.of()));

    return pedido;
  }

  private CounterofferResponse toResponse(Contraoferta contraoferta) {
    Usuario usuario = usuarioRepository.findById(contraoferta.getEntregadorId()).orElse(null);
    Entregador entregador =
        entregadorRepository.findById(contraoferta.getEntregadorId()).orElse(null);

    return new CounterofferResponse(
        contraoferta.getId(),
        contraoferta.getPedidoId(),
        contraoferta.getEntregadorId(),
        usuario == null ? null : usuario.getNomeExibicao(),
        entregador == null ? null : entregador.getScore(),
        contraoferta.getValorProposto(),
        contraoferta.getStatus(),
        contraoferta.getCriadoEm(),
        contraoferta.getRespondidoEm());
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
