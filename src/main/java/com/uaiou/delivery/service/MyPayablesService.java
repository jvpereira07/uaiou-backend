package com.uaiou.delivery.service;

import com.uaiou.delivery.dto.PayablesResponse;
import com.uaiou.delivery.entity.LancamentoFrete;
import com.uaiou.delivery.repository.LancamentoFreteRepository;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.money.Money;
import com.uaiou.users.entity.Usuario;
import com.uaiou.users.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@code GET /me/payables} (RF-18.6) — espelho do estabelecimento, agrupado por entregador. */
@Service
public class MyPayablesService {

  private final LancamentoFreteRepository lancamentoFreteRepository;
  private final PedidoRepository pedidoRepository;
  private final UsuarioRepository usuarioRepository;

  public MyPayablesService(
      LancamentoFreteRepository lancamentoFreteRepository,
      PedidoRepository pedidoRepository,
      UsuarioRepository usuarioRepository) {
    this.lancamentoFreteRepository = lancamentoFreteRepository;
    this.pedidoRepository = pedidoRepository;
    this.usuarioRepository = usuarioRepository;
  }

  @Transactional(readOnly = true)
  public PayablesResponse get(UUID estabelecimentoId) {
    List<LancamentoFrete> todos =
        lancamentoFreteRepository.findByEstabelecimentoIdOrderByCriadoEmDesc(estabelecimentoId);

    Map<UUID, Pedido> pedidosPorId = new LinkedHashMap<>();
    pedidoRepository
        .findAllById(todos.stream().map(LancamentoFrete::getPedidoId).toList())
        .forEach(pedido -> pedidosPorId.put(pedido.getId(), pedido));

    Map<UUID, Usuario> usuariosPorId = new LinkedHashMap<>();
    usuarioRepository
        .findAllById(todos.stream().map(LancamentoFrete::getEntregadorId).distinct().toList())
        .forEach(usuario -> usuariosPorId.put(usuario.getId(), usuario));

    Map<UUID, List<LancamentoFrete>> porEntregador =
        todos.stream()
            .collect(
                Collectors.groupingBy(
                    LancamentoFrete::getEntregadorId, LinkedHashMap::new, Collectors.toList()));

    List<PayablesResponse.ByCourier> grupos =
        porEntregador.entrySet().stream()
            .map(
                entrada -> {
                  UUID entregadorId = entrada.getKey();
                  List<LancamentoFrete> lancamentos = entrada.getValue();
                  Usuario usuario = usuariosPorId.get(entregadorId);
                  List<PayablesResponse.Entry> entradas =
                      lancamentos.stream()
                          .map(
                              lancamento ->
                                  new PayablesResponse.Entry(
                                      lancamento.getId(),
                                      lancamento.getPedidoId(),
                                      numeroDoPedido(pedidosPorId, lancamento.getPedidoId()),
                                      lancamento.getValor(),
                                      lancamento.getStatus(),
                                      lancamento.getCriadoEm(),
                                      lancamento.getTipo()))
                          .toList();
                  return new PayablesResponse.ByCourier(
                      entregadorId,
                      usuario == null ? null : usuario.getNomeExibicao(),
                      somar(lancamentos),
                      entradas);
                })
            .toList();

    return new PayablesResponse(somar(todos), grupos);
  }

  private String numeroDoPedido(Map<UUID, Pedido> pedidosPorId, UUID pedidoId) {
    Pedido pedido = pedidosPorId.get(pedidoId);
    return pedido == null ? null : pedido.getNumero();
  }

  private Money somar(List<LancamentoFrete> lancamentos) {
    return lancamentos.stream().map(LancamentoFrete::getValor).reduce(Money.ZERO, Money::add);
  }
}
