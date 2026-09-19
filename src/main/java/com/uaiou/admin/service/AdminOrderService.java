package com.uaiou.admin.service;

import com.uaiou.admin.dto.AdminOrderAction;
import com.uaiou.admin.dto.AdminOrderActionRequest;
import com.uaiou.admin.dto.AdminOrderDetail;
import com.uaiou.admin.dto.AdminOrderSummary;
import com.uaiou.admin.dto.AdminOrdersOverview;
import com.uaiou.admin.entity.RegistroAuditoria;
import com.uaiou.admin.repository.RegistroAuditoriaRepository;
import com.uaiou.counteroffers.entity.Contraoferta;
import com.uaiou.counteroffers.repository.ContraofertaRepository;
import com.uaiou.delivery.entity.ContingenciaOtp;
import com.uaiou.delivery.entity.EvidenciaEntrega;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.repository.ContingenciaOtpRepository;
import com.uaiou.delivery.repository.EvidenciaEntregaRepository;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.orders.OrderLifecycleEvents.InterventionOrigin;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.DesistenciaPedido;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.DesistenciaPedidoRepository;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.orders.service.PlatformInterventionService;
import com.uaiou.orders.timeout.ConfiguracaoTimeoutRepository;
import com.uaiou.orders.timeout.OcorrenciaTimeout;
import com.uaiou.orders.timeout.OcorrenciaTimeoutRepository;
import com.uaiou.orders.timeout.OrderTimeoutService;
import com.uaiou.shared.error.BadRequestException;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.Paginator;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-21.8 — a visão que o suporte precisa e que nenhuma tabela isolada dá: a timeline é montada por
 * COMPOSIÇÃO, nunca é uma tabela própria. RF-21.9: o código de entrega nunca entra aqui, nem
 * cifrado — quem investiga conluio não pode ser o mesmo canal que vazaria o código.
 *
 * <p>Também é a porta das intervenções manuais ({@link AdminOrderAction}): toda mudança de estado
 * feita pelo admin grava {@code registro_auditoria} na mesma transação (RF-07.1).
 */
@Service
public class AdminOrderService {

  static final String REFERENCIA_PEDIDO = "pedido";

  private final PedidoRepository pedidoRepository;
  private final ContraofertaRepository contraofertaRepository;
  private final ContingenciaOtpRepository contingenciaOtpRepository;
  private final EvidenciaEntregaRepository evidenciaEntregaRepository;
  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final DesistenciaPedidoRepository desistenciaPedidoRepository;
  private final OcorrenciaTimeoutRepository ocorrenciaTimeoutRepository;
  private final ConfiguracaoTimeoutRepository configuracaoTimeoutRepository;
  private final OrderTimeoutService orderTimeoutService;
  private final RegistroAuditoriaRepository registroAuditoriaRepository;
  private final UsuarioRepository usuarioRepository;
  private final PlatformInterventionService interventionService;
  private final AuditService auditService;

  public AdminOrderService(
      PedidoRepository pedidoRepository,
      ContraofertaRepository contraofertaRepository,
      ContingenciaOtpRepository contingenciaOtpRepository,
      EvidenciaEntregaRepository evidenciaEntregaRepository,
      LancamentoFreteRepository lancamentoFreteRepository,
      DesistenciaPedidoRepository desistenciaPedidoRepository,
      OcorrenciaTimeoutRepository ocorrenciaTimeoutRepository,
      ConfiguracaoTimeoutRepository configuracaoTimeoutRepository,
      OrderTimeoutService orderTimeoutService,
      RegistroAuditoriaRepository registroAuditoriaRepository,
      UsuarioRepository usuarioRepository,
      PlatformInterventionService interventionService,
      AuditService auditService) {
    this.pedidoRepository = pedidoRepository;
    this.contraofertaRepository = contraofertaRepository;
    this.contingenciaOtpRepository = contingenciaOtpRepository;
    this.evidenciaEntregaRepository = evidenciaEntregaRepository;
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.desistenciaPedidoRepository = desistenciaPedidoRepository;
    this.ocorrenciaTimeoutRepository = ocorrenciaTimeoutRepository;
    this.configuracaoTimeoutRepository = configuracaoTimeoutRepository;
    this.orderTimeoutService = orderTimeoutService;
    this.registroAuditoriaRepository = registroAuditoriaRepository;
    this.usuarioRepository = usuarioRepository;
    this.interventionService = interventionService;
    this.auditService = auditService;
  }

  // ------------------------------------------------------------------ histórico

  /**
   * Histórico de entregas do sistema inteiro. {@code search} casa com o número do pedido ou com o
   * bairro de destino; {@code status} aceita vários valores separados por vírgula.
   */
  @Transactional(readOnly = true)
  public PageResponse<AdminOrderSummary> list(
      String status,
      String search,
      UUID merchantId,
      UUID courierId,
      Instant from,
      Instant to,
      PagingRequest paging,
      String baseUri) {
    Specification<Pedido> spec =
        filtro(parseStatus(status), search, merchantId, courierId, from, to);
    Page<Pedido> page =
        pedidoRepository.findAll(
            spec,
            PageRequest.of(paging.page() - 1, paging.perPage(), Sort.by("criadoEm").descending()));

    Map<UUID, String> nomes =
        nomes(
            page.getContent().stream()
                .flatMap(p -> Stream.of(p.getEstabelecimentoId(), p.getEntregadorId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

    List<AdminOrderSummary> content =
        page.getContent().stream()
            .map(
                p ->
                    new AdminOrderSummary(
                        p.getId(),
                        p.getNumero(),
                        p.getStatus(),
                        ref(p.getEstabelecimentoId(), nomes),
                        ref(p.getEntregadorId(), nomes),
                        p.getFreteProposto(),
                        p.getFreteFinal(),
                        p.getDestBairro(),
                        p.getCriadoEm(),
                        p.getAceitoEm(),
                        p.getColetadoEm(),
                        p.getFinalizadoEm(),
                        p.getCanceladoEm(),
                        p.getCancelamentoMotivo()))
            .toList();
    return Paginator.paginate(content, page.getTotalElements(), paging, baseUri);
  }

  @Transactional(readOnly = true)
  public AdminOrdersOverview overview() {
    Map<String, Long> porStatus = new LinkedHashMap<>();
    for (OrderStatus status : OrderStatus.values()) {
      porStatus.put(status.toJson(), 0L);
    }
    for (Object[] linha : pedidoRepository.contarPorStatus()) {
      porStatus.put(((OrderStatus) linha[0]).toJson(), (Long) linha[1]);
    }

    Map<String, Integer> vencidos = new LinkedHashMap<>();
    Instant agora = Instant.now();
    configuracaoTimeoutRepository.findAll().stream()
        .sorted(Comparator.comparing(c -> c.getRegra().ordinal()))
        .forEach(
            c ->
                vencidos.put(
                    c.getRegra().code(),
                    orderTimeoutService.contarVencidos(c.getRegra(), agora.minus(c.getDuracao()))));
    return new AdminOrdersOverview(porStatus, vencidos);
  }

  // ------------------------------------------------------------------ intervenção

  /**
   * Lock primeiro, validação depois (mesmo padrão de RF-26.15): o estado que conta é o de dentro do
   * lock — o entregador pode ter finalizado no instante em que o admin clicou.
   */
  @Transactional
  public AdminOrderDetail act(UUID adminId, UUID pedidoId, AdminOrderActionRequest request) {
    Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElseThrow(this::notFound);
    AdminOrderAction acao = request.action();
    OrderStatus anterior = pedido.getStatus();
    if (!acao.permitidaEm(anterior)) {
      throw new ConflictException(
          "ORDER_ACTION_NOT_ALLOWED",
          "Ação \""
              + acao.code()
              + "\" não é permitida com o pedido em \""
              + anterior.toJson()
              + "\".",
          Map.of("status", anterior.toJson(), "action", acao.code()));
    }
    String motivo = request.reason().strip();

    switch (acao) {
      case CANCEL -> interventionService.cancelar(pedido, InterventionOrigin.ADMIN, motivo);
      case RETURN_TO_SHOWCASE ->
          interventionService.devolverAVitrine(pedido, InterventionOrigin.ADMIN);
      case MARK_PICKED_UP -> pedido.confirmarColeta();
      case FINALIZE -> {
        pedido.finalizarPeloAdmin();
        lancarFreteSeFaltar(pedido);
      }
    }

    auditService.record(
        adminId,
        "order_" + acao.code(),
        REFERENCIA_PEDIDO,
        pedidoId,
        "[" + anterior.toJson() + " → " + pedido.getStatus().toJson() + "] " + motivo);

    pedidoRepository.flush();
    return get(pedidoId);
  }

  /**
   * RF-15.9 — finalizar é o que cria o recebível do entregador. Finalização manual sem lançamento
   * deixaria a entrega feita sem nada a receber; contestável já tem o seu.
   */
  private void lancarFreteSeFaltar(Pedido pedido) {
    if (lancamentoFreteRepository.existsByPedidoId(pedido.getId())) {
      return;
    }
    lancamentoFreteRepository.save(
        new LancamentoFrete(
            UuidV7.next(),
            pedido.getId(),
            pedido.getEntregadorId(),
            pedido.getEstabelecimentoId(),
            pedido.getFreteFinal()));
  }

  // ------------------------------------------------------------------ espelho

  @Transactional(readOnly = true)
  public AdminOrderDetail get(UUID pedidoId) {
    Pedido pedido = pedidoRepository.findById(pedidoId).orElseThrow(this::notFound);

    Usuario estabelecimento =
        usuarioRepository.findById(pedido.getEstabelecimentoId()).orElse(null);
    Usuario entregador =
        pedido.getEntregadorId() == null
            ? null
            : usuarioRepository.findById(pedido.getEntregadorId()).orElse(null);

    List<AdminOrderDetail.TimelineEvent> timeline = new ArrayList<>();
    timeline.add(
        evento(pedido.getCriadoEm(), "order.created", Map.of("number", pedido.getNumero())));
    if (pedido.getAceitoEm() != null) {
      timeline.add(
          evento(
              pedido.getAceitoEm(),
              "order.assigned",
              entregador == null ? Map.of() : Map.of("courierId", entregador.getId().toString())));
    }
    if (pedido.getChegouEm() != null) {
      timeline.add(evento(pedido.getChegouEm(), "order.courier_arrived", Map.of()));
    }
    if (pedido.getColetadoEm() != null) {
      timeline.add(evento(pedido.getColetadoEm(), "order.picked_up", Map.of()));
    }

    for (Contraoferta contraoferta :
        contraofertaRepository.findByPedidoIdOrderByCriadoEmDesc(pedidoId)) {
      timeline.add(
          evento(
              contraoferta.getCriadoEm(),
              "counteroffer.proposed",
              Map.of(
                  "courierId",
                  contraoferta.getEntregadorId().toString(),
                  "amount",
                  contraoferta.getValorProposto().toString())));
      if (contraoferta.getRespondidoEm() != null) {
        timeline.add(
            evento(
                contraoferta.getRespondidoEm(),
                "counteroffer.decided",
                Map.of("status", contraoferta.getStatus().toString())));
      }
    }

    for (DesistenciaPedido desistencia : desistenciaPedidoRepository.findByPedidoId(pedidoId)) {
      timeline.add(
          evento(
              desistencia.getCriadoEm(),
              "order.courier_withdrew",
              Map.of(
                  "courierId",
                  desistencia.getEntregadorId().toString(),
                  "reason",
                  desistencia.getMotivo())));
    }

    for (ContingenciaOtp contingencia :
        contingenciaOtpRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)) {
      timeline.add(
          evento(
              contingencia.getCriadoEm(),
              "delivery.code_contingency",
              Map.of(
                  "step",
                  contingencia.getDegrau(),
                  "channel",
                  contingencia.getCanal().toString())));
    }

    EvidenciaEntrega evidencia = evidenciaEntregaRepository.findByPedidoId(pedidoId).orElse(null);
    if (evidencia != null) {
      timeline.add(
          evento(
              evidencia.getRegistradoEm(),
              "delivery.completed",
              Map.of("mode", evidencia.getTipoFinalizacao().toString())));
    }

    LancamentoFrete lancamento = lancamentoFreteRepository.findByPedidoId(pedidoId).orElse(null);
    if (lancamento != null) {
      timeline.add(
          evento(
              lancamento.getCriadoEm(),
              "ledger.entry_created",
              Map.of(
                  "amount",
                  lancamento.getValor().toString(),
                  "status",
                  lancamento.getStatus().toString())));
    }

    for (OcorrenciaTimeout ocorrencia :
        ocorrenciaTimeoutRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId)) {
      timeline.add(
          evento(
              ocorrencia.getCriadoEm(),
              "timeout." + ocorrencia.getRegra().code(),
              Map.of(
                  "action",
                  ocorrencia.getAcao().code(),
                  "previousStatus",
                  ocorrencia.getStatusAnterior().toJson())));
    }

    List<RegistroAuditoria> intervencoes =
        registroAuditoriaRepository.findByReferenciaTipoAndReferenciaIdOrderByCriadoEmAsc(
            REFERENCIA_PEDIDO, pedidoId);
    Map<UUID, String> admins =
        nomes(intervencoes.stream().map(RegistroAuditoria::getAdminId).collect(Collectors.toSet()));
    for (RegistroAuditoria registro : intervencoes) {
      Map<String, Object> detalhes = new HashMap<>();
      detalhes.put("admin", admins.getOrDefault(registro.getAdminId(), "—"));
      if (registro.getMotivo() != null) {
        detalhes.put("reason", registro.getMotivo());
      }
      timeline.add(evento(registro.getCriadoEm(), "admin." + registro.getAcao(), detalhes));
    }

    if (pedido.getCanceladoEm() != null) {
      timeline.add(
          evento(
              pedido.getCanceladoEm(),
              "order.cancelled",
              Map.of("reason", pedido.getCancelamentoMotivo())));
    }

    timeline.sort(Comparator.comparing(AdminOrderDetail.TimelineEvent::at));

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put(
        "adjustments", LinkRef.get("/api/v1/admin/financial-adjustments?orderId=" + pedidoId));
    links.put("actions", new LinkRef("/api/v1/admin/orders/" + pedidoId + "/actions", "POST"));

    List<AdminOrderAction> disponiveis =
        Arrays.stream(AdminOrderAction.values())
            .filter(acao -> acao.permitidaEm(pedido.getStatus()))
            .toList();

    return new AdminOrderDetail(
        pedido.getId(),
        pedido.getNumero(),
        pedido.getStatus(),
        estabelecimento == null
            ? null
            : new AdminOrderDetail.Ref(estabelecimento.getId(), estabelecimento.getNomeExibicao()),
        entregador == null
            ? null
            : new AdminOrderDetail.Ref(entregador.getId(), entregador.getNomeExibicao()),
        pedido.getFreteProposto(),
        pedido.getFreteFinal(),
        pedido.getStatus() == OrderStatus.CONTESTABLE_FINALIZED,
        pedido.getCriadoEm(),
        pedido.getCanceladoEm() == null
            ? null
            : new AdminOrderDetail.Cancellation(
                pedido.getCancelamentoMotivo(),
                pedido.getCancelamentoNota(),
                pedido.getCanceladoEm()),
        disponiveis,
        timeline,
        links);
  }

  // ------------------------------------------------------------------ apoio

  private static List<OrderStatus> parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return List.of();
    }
    try {
      return Arrays.stream(status.split(","))
          .map(String::strip)
          .filter(s -> !s.isEmpty())
          .map(OrderStatus::fromJson)
          .toList();
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("INVALID_STATUS", "Status de pedido desconhecido: " + status);
    }
  }

  private static Specification<Pedido> filtro(
      List<OrderStatus> status,
      String search,
      UUID merchantId,
      UUID courierId,
      Instant from,
      Instant to) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (!status.isEmpty()) {
        predicates.add(root.get("status").in(status));
      }
      if (search != null && !search.isBlank()) {
        String termo = search.strip();
        String like = "%" + termo.toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.equal(root.get("numero"), termo),
                cb.like(cb.lower(root.get("destBairro")), like)));
      }
      if (merchantId != null) {
        predicates.add(cb.equal(root.get("estabelecimentoId"), merchantId));
      }
      if (courierId != null) {
        predicates.add(cb.equal(root.get("entregadorId"), courierId));
      }
      if (from != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("criadoEm"), from));
      }
      if (to != null) {
        predicates.add(cb.lessThan(root.get("criadoEm"), to));
      }
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private Map<UUID, String> nomes(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return usuarioRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Usuario::getId, Usuario::getNomeExibicao, (a, b) -> a));
  }

  private static AdminOrderDetail.Ref ref(UUID id, Map<UUID, String> nomes) {
    return id == null ? null : new AdminOrderDetail.Ref(id, nomes.get(id));
  }

  private NotFoundException notFound() {
    return new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado.");
  }

  private AdminOrderDetail.TimelineEvent evento(
      Instant at, String nome, Map<String, Object> detalhes) {
    return new AdminOrderDetail.TimelineEvent(at, nome, detalhes);
  }
}
