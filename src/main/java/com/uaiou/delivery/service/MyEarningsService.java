package com.uaiou.delivery.service;

import com.uaiou.delivery.LedgerStatus;
import com.uaiou.delivery.dto.EarningsResponse;
import com.uaiou.delivery.dto.SettlementRequest;
import com.uaiou.delivery.dto.SettlementResponse;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.shared.pagination.PageMeta;
import com.uaiou.shared.pagination.PagingRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code GET /me/earnings} / {@code POST /me/earnings/settlements} (RF-18.2 a RF-18.5). */
@Service
public class MyEarningsService {

  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final PedidoRepository pedidoRepository;

  public MyEarningsService(
      LancamentoFreteRepository lancamentoFreteRepository, PedidoRepository pedidoRepository) {
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.pedidoRepository = pedidoRepository;
  }

  @Transactional(readOnly = true)
  public EarningsResponse get(UUID entregadorId, PagingRequest paging) {
    List<LancamentoFrete> todos = lancamentoFreteRepository.findByEntregadorId(entregadorId);
    Money total = somar(todos);
    Money aReceber =
        somar(todos.stream().filter(l -> l.getStatus() == LedgerStatus.RECEIVABLE).toList());
    Money acertado =
        somar(todos.stream().filter(l -> l.getStatus() == LedgerStatus.SETTLED).toList());

    Page<LancamentoFrete> pagina =
        lancamentoFreteRepository.findByEntregadorIdOrderByCriadoEmDesc(
            entregadorId, PageRequest.of(paging.page() - 1, paging.perPage()));
    Map<UUID, Pedido> pedidosPorId = pedidosPorId(pagina.getContent());

    List<EarningsResponse.Entry> entradas =
        pagina.getContent().stream()
            .map(
                lancamento ->
                    new EarningsResponse.Entry(
                        lancamento.getId(),
                        lancamento.getPedidoId(),
                        numeroDoPedido(pedidosPorId, lancamento.getPedidoId()),
                        lancamento.getValor(),
                        lancamento.getStatus(),
                        lancamento.getCriadoEm(),
                        lancamento.getAcertadoEm()))
            .toList();

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put("self", LinkRef.get("/api/v1/me/earnings"));

    return new EarningsResponse(
        new EarningsResponse.Summary(total, aReceber, acertado),
        entradas,
        new PageMeta(paging.page(), paging.perPage(), pagina.getTotalElements()),
        links);
  }

  /**
   * RF-18.5 — idempotente e restrito: já {@code acertado} → 409; lançamento de outro entregador →
   * 403. Confirma um a um dentro do mesmo laço para que o primeiro erro real (não o "já feito")
   * pare a operação — um settlement em lote não deveria confirmar metade e falhar calado no resto.
   */
  @Transactional
  public SettlementResponse settle(UUID entregadorId, SettlementRequest request) {
    List<LancamentoFrete> lancamentos =
        lancamentoFreteRepository.findAllById(request.lancamentoIds());
    if (lancamentos.size() != request.lancamentoIds().size()) {
      throw new NotFoundException("LANCAMENTO_NOT_FOUND", "Um ou mais lançamentos não existem.");
    }
    for (LancamentoFrete lancamento : lancamentos) {
      if (!lancamento.pertenceAoEntregador(entregadorId)) {
        throw new ForbiddenException(
            "NOT_YOUR_EARNING", "Você só confirma lançamentos dos seus próprios pedidos.");
      }
      if (lancamento.getStatus() == LedgerStatus.SETTLED) {
        throw new ConflictException(
            "ALREADY_SETTLED", "Este lançamento já foi confirmado como recebido.");
      }
    }
    for (LancamentoFrete lancamento : lancamentos) {
      lancamento.acertar(entregadorId);
    }
    lancamentoFreteRepository.saveAll(lancamentos);

    return new SettlementResponse(request.lancamentoIds(), Instant.now());
  }

  private Map<UUID, Pedido> pedidosPorId(List<LancamentoFrete> lancamentos) {
    List<UUID> pedidoIds = lancamentos.stream().map(LancamentoFrete::getPedidoId).toList();
    Map<UUID, Pedido> mapa = new LinkedHashMap<>();
    pedidoRepository.findAllById(pedidoIds).forEach(pedido -> mapa.put(pedido.getId(), pedido));
    return mapa;
  }

  private String numeroDoPedido(Map<UUID, Pedido> pedidosPorId, UUID pedidoId) {
    Pedido pedido = pedidosPorId.get(pedidoId);
    return pedido == null ? null : pedido.getNumero();
  }

  private Money somar(List<LancamentoFrete> lancamentos) {
    return lancamentos.stream().map(LancamentoFrete::getValor).reduce(Money.ZERO, Money::add);
  }
}
