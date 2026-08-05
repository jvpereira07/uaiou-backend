package com.uaiou.admin.service;

import com.uaiou.admin.dto.AdminOrderDetail;
import com.uaiou.counteroffers.entity.Contraoferta;
import com.uaiou.counteroffers.repository.ContraofertaRepository;
import com.uaiou.delivery.entity.ContingenciaOtp;
import com.uaiou.delivery.entity.EvidenciaEntrega;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.repository.ContingenciaOtpRepository;
import com.uaiou.delivery.repository.EvidenciaEntregaRepository;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-21.8 — a visão que o suporte precisa e que nenhuma tabela isolada dá: a timeline é montada por
 * COMPOSIÇÃO, nunca é uma tabela própria. RF-21.9: o código de entrega nunca entra aqui, nem
 * cifrado — quem investiga conluio não pode ser o mesmo canal que vazaria o código.
 */
@Service
public class AdminOrderService {

  private final PedidoRepository pedidoRepository;
  private final ContraofertaRepository contraofertaRepository;
  private final ContingenciaOtpRepository contingenciaOtpRepository;
  private final EvidenciaEntregaRepository evidenciaEntregaRepository;
  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final UsuarioRepository usuarioRepository;

  public AdminOrderService(
      PedidoRepository pedidoRepository,
      ContraofertaRepository contraofertaRepository,
      ContingenciaOtpRepository contingenciaOtpRepository,
      EvidenciaEntregaRepository evidenciaEntregaRepository,
      LancamentoFreteRepository lancamentoFreteRepository,
      UsuarioRepository usuarioRepository) {
    this.pedidoRepository = pedidoRepository;
    this.contraofertaRepository = contraofertaRepository;
    this.contingenciaOtpRepository = contingenciaOtpRepository;
    this.evidenciaEntregaRepository = evidenciaEntregaRepository;
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.usuarioRepository = usuarioRepository;
  }

  @Transactional(readOnly = true)
  public AdminOrderDetail get(UUID pedidoId) {
    Pedido pedido =
        pedidoRepository
            .findById(pedidoId)
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado."));

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

    timeline.sort(Comparator.comparing(AdminOrderDetail.TimelineEvent::at));

    Map<String, LinkRef> links = new LinkedHashMap<>();
    links.put(
        "adjustments", LinkRef.get("/api/v1/admin/financial-adjustments?orderId=" + pedidoId));

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
        pedido.getFreteFinal(),
        pedido.getStatus() == OrderStatus.CONTESTABLE_FINALIZED,
        timeline,
        links);
  }

  private AdminOrderDetail.TimelineEvent evento(
      java.time.Instant at, String nome, Map<String, Object> detalhes) {
    return new AdminOrderDetail.TimelineEvent(at, nome, detalhes);
  }
}
