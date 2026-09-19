package com.uaiou.admin.service;

import com.uaiou.admin.dto.AdminOrderDetail;
import com.uaiou.admin.dto.TimeoutOccurrenceResponse;
import com.uaiou.admin.dto.TimeoutRunResponse;
import com.uaiou.admin.dto.TimeoutSettingResponse;
import com.uaiou.admin.dto.UpdateTimeoutRequest;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.orders.timeout.ConfiguracaoTimeout;
import com.uaiou.orders.timeout.ConfiguracaoTimeoutRepository;
import com.uaiou.orders.timeout.OcorrenciaTimeout;
import com.uaiou.orders.timeout.OcorrenciaTimeoutRepository;
import com.uaiou.orders.timeout.OrderTimeoutRule;
import com.uaiou.orders.timeout.OrderTimeoutService;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.PageResponse;
import com.uaiou.shared.pagination.Paginator;
import com.uaiou.shared.pagination.PagingRequest;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão das regras de timeout pelo painel: leitura, ajuste, histórico e execução manual. */
@Service
public class AdminTimeoutService {

  private final ConfiguracaoTimeoutRepository configuracaoRepository;
  private final OcorrenciaTimeoutRepository ocorrenciaRepository;
  private final OrderTimeoutService orderTimeoutService;
  private final PedidoRepository pedidoRepository;
  private final UsuarioRepository usuarioRepository;
  private final AuditService auditService;

  public AdminTimeoutService(
      ConfiguracaoTimeoutRepository configuracaoRepository,
      OcorrenciaTimeoutRepository ocorrenciaRepository,
      OrderTimeoutService orderTimeoutService,
      PedidoRepository pedidoRepository,
      UsuarioRepository usuarioRepository,
      AuditService auditService) {
    this.configuracaoRepository = configuracaoRepository;
    this.ocorrenciaRepository = ocorrenciaRepository;
    this.orderTimeoutService = orderTimeoutService;
    this.pedidoRepository = pedidoRepository;
    this.usuarioRepository = usuarioRepository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public List<TimeoutSettingResponse> list() {
    Instant agora = Instant.now();
    return configuracaoRepository.findAll().stream()
        .sorted(Comparator.comparing(c -> c.getRegra().ordinal()))
        .map(c -> toResponse(c, agora))
        .toList();
  }

  @Transactional
  public TimeoutSettingResponse update(
      UUID adminId, OrderTimeoutRule regra, UpdateTimeoutRequest request) {
    ConfiguracaoTimeout config =
        configuracaoRepository
            .findById(regra.dbKey())
            .orElseThrow(
                () ->
                    new NotFoundException("TIMEOUT_NOT_FOUND", "Regra de timeout não encontrada."));
    config.atualizar(adminId, request.minutes(), request.active());
    String estado = request.active() ? "ligada" : "desligada";
    auditService.record(
        adminId,
        "timeout_updated",
        null,
        null,
        regra.code() + ": " + request.minutes() + " min, " + estado);
    return toResponse(config, Instant.now());
  }

  /**
   * Execução imediata das regras LIGADAS — mesma rotina do job, sem esperar o próximo minuto. Fora
   * de transação: cada pedido abre a sua (ver {@link OrderTimeoutService}).
   */
  public TimeoutRunResponse runNow(UUID adminId) {
    Map<OrderTimeoutRule, Integer> afetados = orderTimeoutService.aplicarRegrasAtivas();
    Map<String, Integer> porCodigo = new LinkedHashMap<>();
    afetados.forEach((regra, total) -> porCodigo.put(regra.code(), total));
    auditService.record(adminId, "timeouts_run", null, null, porCodigo.toString());
    return new TimeoutRunResponse(porCodigo);
  }

  @Transactional(readOnly = true)
  public PageResponse<TimeoutOccurrenceResponse> occurrences(PagingRequest paging, String baseUri) {
    Page<OcorrenciaTimeout> page =
        ocorrenciaRepository.findAllByOrderByCriadoEmDesc(
            PageRequest.of(paging.page() - 1, paging.perPage()));
    Map<UUID, String> numeros =
        pedidoRepository
            .findAllById(page.getContent().stream().map(OcorrenciaTimeout::getPedidoId).toList())
            .stream()
            .collect(Collectors.toMap(Pedido::getId, Pedido::getNumero));
    List<TimeoutOccurrenceResponse> content =
        page.getContent().stream()
            .map(
                o ->
                    new TimeoutOccurrenceResponse(
                        o.getId(),
                        o.getPedidoId(),
                        numeros.get(o.getPedidoId()),
                        o.getRegra(),
                        o.getAcao(),
                        o.getStatusAnterior(),
                        o.getCriadoEm()))
            .toList();
    return Paginator.paginate(content, page.getTotalElements(), paging, baseUri);
  }

  private TimeoutSettingResponse toResponse(ConfiguracaoTimeout config, Instant agora) {
    OrderTimeoutRule regra = config.getRegra();
    UUID autorId = config.getAtualizadoPor();
    AdminOrderDetail.Ref autor =
        autorId == null
            ? null
            : usuarioRepository
                .findById(autorId)
                .map(Usuario::getNomeExibicao)
                .map(nome -> new AdminOrderDetail.Ref(autorId, nome))
                .orElse(null);
    return new TimeoutSettingResponse(
        regra,
        regra.action(),
        config.getDuracaoMinutos(),
        config.isAtivo(),
        orderTimeoutService.contarVencidos(regra, agora.minus(config.getDuracao())),
        config.getAtualizadoEm(),
        autor);
  }
}
