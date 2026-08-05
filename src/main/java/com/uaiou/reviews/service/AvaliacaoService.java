package com.uaiou.reviews.service;

import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.reviews.ReviewCreatedEvent;
import com.uaiou.reviews.config.ReviewsProperties;
import com.uaiou.reviews.dto.CreateReviewRequest;
import com.uaiou.reviews.dto.PendingReviewEntry;
import com.uaiou.reviews.dto.ReceivedReviewsResponse;
import com.uaiou.reviews.dto.ReviewResponse;
import com.uaiou.reviews.entity.Avaliacao;
import com.uaiou.reviews.repository.AvaliacaoRepository;
import com.uaiou.shared.error.BusinessRuleException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-19.1 a RF-19.8 — avaliação mútua pós-entrega. */
@Service
public class AvaliacaoService {

  private static final List<OrderStatus> FINALIZADOS =
      List.of(OrderStatus.FINALIZED, OrderStatus.CONTESTABLE_FINALIZED);

  private final PedidoRepository pedidoRepository;
  private final AvaliacaoRepository avaliacaoRepository;
  private final UsuarioRepository usuarioRepository;
  private final ReviewsProperties properties;
  private final ApplicationEventPublisher events;

  public AvaliacaoService(
      PedidoRepository pedidoRepository,
      AvaliacaoRepository avaliacaoRepository,
      UsuarioRepository usuarioRepository,
      ReviewsProperties properties,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.avaliacaoRepository = avaliacaoRepository;
    this.usuarioRepository = usuarioRepository;
    this.properties = properties;
    this.events = events;
  }

  /**
   * RF-19.2/RF-19.3 — alvo derivado do pedido, nunca do cliente. Autor que não é parte do pedido →
   * 403 (não revela mais que o necessário sobre o pedido de terceiro).
   */
  @Transactional
  public ReviewResponse create(UUID autorId, UUID pedidoId, CreateReviewRequest request) {
    Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(this::notFound);
    if (!FINALIZADOS.contains(pedido.getStatus())) {
      throw new BusinessRuleException(
          "ORDER_NOT_FINALIZED", "Só é possível avaliar um pedido finalizado.", "RN-03.1");
    }

    UUID alvoId;
    boolean autorEhEstabelecimento = pedido.pertenceAoEstabelecimento(autorId);
    if (autorEhEstabelecimento) {
      alvoId = pedido.getEntregadorId();
    } else if (pedido.estaAtribuidoA(autorId)) {
      alvoId = pedido.getEstabelecimentoId();
    } else {
      throw new ForbiddenException("NOT_A_PARTY", "Você não participou deste pedido.");
    }

    if (avaliacaoRepository.existsByPedidoIdAndAutorId(pedidoId, autorId)) {
      throw new ConflictException(
          "ALREADY_REVIEWED", "Você já avaliou este pedido. RN-02.1: uma rodada só.");
    }

    Avaliacao avaliacao =
        avaliacaoRepository.save(
            new Avaliacao(
                UuidV7.next(), pedidoId, autorId, alvoId, request.rating(), request.comment()));

    events.publishEvent(new ReviewCreatedEvent(alvoId, autorEhEstabelecimento));

    return new ReviewResponse(
        avaliacao.getId(),
        pedidoId,
        alvoId,
        avaliacao.getNota(),
        avaliacao.getComentario(),
        avaliacao.getCriadoEm());
  }

  /**
   * RF-19.8 — {@code direction=pending}: entregas finalizadas, dentro do prazo, ainda sem avaliação
   * minha.
   */
  @Transactional(readOnly = true)
  public List<PendingReviewEntry> pending(UUID usuarioId, boolean isCourier) {
    List<Pedido> candidatos =
        isCourier
            ? pedidoRepository.findByEntregadorIdAndStatusIn(usuarioId, FINALIZADOS)
            : pedidoRepository.findByEstabelecimentoIdAndStatusIn(usuarioId, FINALIZADOS);

    Instant agora = Instant.now();
    Map<UUID, Usuario> usuarios = new LinkedHashMap<>();

    return candidatos.stream()
        .filter(pedido -> pedido.getFinalizadoEm() != null)
        .filter(pedido -> pedido.getFinalizadoEm().plus(properties.window()).isAfter(agora))
        .filter(
            pedido -> !avaliacaoRepository.existsByPedidoIdAndAutorId(pedido.getId(), usuarioId))
        .map(
            pedido -> {
              UUID contraparteId =
                  isCourier ? pedido.getEstabelecimentoId() : pedido.getEntregadorId();
              Usuario contraparte =
                  usuarios.computeIfAbsent(
                      contraparteId, id -> usuarioRepository.findById(id).orElse(null));
              return new PendingReviewEntry(
                  pedido.getId(),
                  pedido.getNumero(),
                  contraparteId,
                  contraparte == null ? null : contraparte.getNomeExibicao(),
                  pedido.getFinalizadoEm().plus(properties.window()));
            })
        .toList();
  }

  /**
   * RF-19.7 — leitura cruzada retida: só aparece o que já é visível (ambas existem, ou o prazo já
   * passou). RF-19.6 — {@code activeRate} para uma média não passar por opinião real quando é
   * padrão automático.
   */
  @Transactional(readOnly = true)
  public ReceivedReviewsResponse received(UUID usuarioId) {
    List<Avaliacao> recebidas = avaliacaoRepository.findByAlvoId(usuarioId);
    Instant agora = Instant.now();

    List<Avaliacao> visiveis =
        recebidas.stream().filter(avaliacao -> visivel(avaliacao, usuarioId, agora)).toList();

    Map<UUID, Pedido> pedidos = new LinkedHashMap<>();
    pedidoRepository
        .findAllById(visiveis.stream().map(Avaliacao::getPedidoId).toList())
        .forEach(pedido -> pedidos.put(pedido.getId(), pedido));

    List<ReceivedReviewsResponse.Entry> entradas =
        visiveis.stream()
            .map(
                avaliacao ->
                    new ReceivedReviewsResponse.Entry(
                        avaliacao.getId(),
                        avaliacao.getPedidoId(),
                        numeroDoPedido(pedidos, avaliacao.getPedidoId()),
                        avaliacao.getNota(),
                        avaliacao.getComentario(),
                        avaliacao.isAtiva(),
                        avaliacao.getCriadoEm()))
            .toList();

    return new ReceivedReviewsResponse(resumo(visiveis), entradas);
  }

  private boolean visivel(Avaliacao avaliacao, UUID usuarioId, Instant agora) {
    boolean reciprocaExiste =
        avaliacaoRepository.existsByPedidoIdAndAutorId(avaliacao.getPedidoId(), usuarioId);
    if (reciprocaExiste) {
      return true;
    }
    Pedido pedido = pedidoRepository.findById(avaliacao.getPedidoId()).orElse(null);
    return pedido != null
        && pedido.getFinalizadoEm() != null
        && pedido.getFinalizadoEm().plus(properties.window()).isBefore(agora);
  }

  private ReceivedReviewsResponse.Summary resumo(List<Avaliacao> avaliacoes) {
    if (avaliacoes.isEmpty()) {
      return new ReceivedReviewsResponse.Summary(null, null, 0);
    }
    BigDecimal soma =
        avaliacoes.stream()
            .map(a -> BigDecimal.valueOf(a.getNota()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal media = soma.divide(BigDecimal.valueOf(avaliacoes.size()), 2, RoundingMode.HALF_UP);
    long ativas = avaliacoes.stream().filter(Avaliacao::isAtiva).count();
    BigDecimal activeRate =
        BigDecimal.valueOf(ativas)
            .divide(BigDecimal.valueOf(avaliacoes.size()), 2, RoundingMode.HALF_UP);
    return new ReceivedReviewsResponse.Summary(media, activeRate, avaliacoes.size());
  }

  private String numeroDoPedido(Map<UUID, Pedido> pedidos, UUID pedidoId) {
    Pedido pedido = pedidos.get(pedidoId);
    return pedido == null ? null : pedido.getNumero();
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }
}
