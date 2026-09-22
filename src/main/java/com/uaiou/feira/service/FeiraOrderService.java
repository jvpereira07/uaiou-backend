package com.uaiou.feira.service;

import com.uaiou.feira.config.FeiraProperties;
import com.uaiou.feira.dto.FeiraDtos.CapturaAdminResponse;
import com.uaiou.feira.dto.FeiraDtos.CapturaResponse;
import com.uaiou.feira.dto.FeiraDtos.CriarPedidoRequest;
import com.uaiou.feira.dto.FeiraDtos.PedidoAdminResponse;
import com.uaiou.feira.dto.FeiraDtos.PedidoResponse;
import com.uaiou.feira.entity.FeiraCaptura;
import com.uaiou.feira.repository.FeiraCapturaRepository;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.shared.error.ConflictException;
import com.uaiou.shared.error.NotFoundException;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedido-prêmio da feira: publicação pelo admin, vitrine do jogador, captura e entrega
 * (docs/feira/01-fluxos.md, fluxos 3 a 6).
 */
@Service
public class FeiraOrderService {

  private final PedidoRepository pedidoRepository;
  private final FeiraCapturaRepository capturaRepository;
  private final EntregadorRepository entregadorRepository;
  private final FeiraProperties properties;

  public FeiraOrderService(
      PedidoRepository pedidoRepository,
      FeiraCapturaRepository capturaRepository,
      EntregadorRepository entregadorRepository,
      FeiraProperties properties) {
    this.pedidoRepository = pedidoRepository;
    this.capturaRepository = capturaRepository;
    this.entregadorRepository = entregadorRepository;
    this.properties = properties;
  }

  @Transactional
  public PedidoAdminResponse publicar(CriarPedidoRequest request) {
    UUID id = UuidV7.next();
    Pedido pedido =
        Pedido.paraFeira(
            id,
            numeroDe(id),
            properties.estabelecimentoId(),
            request.lat(),
            request.longitude(),
            request.rotulo(),
            request.recompensa().trim(),
            request.limite());
    return toAdmin(pedidoRepository.saveAndFlush(pedido));
  }

  /**
   * O número do pedido é único por estabelecimento (uk_pedido_numero_por_estabelecimento). O
   * produto serializa uma sequência por loja sob trava; aqui todos os pedidos são da mesma loja e
   * não há nota nenhuma para casar, então derivar do próprio id evita a trava e não colide na
   * prática.
   */
  private String numeroDe(UUID id) {
    return "F-" + id.toString().substring(0, 8);
  }

  @Transactional(readOnly = true)
  public List<PedidoAdminResponse> listarParaAdmin() {
    return pedidoRepository.findByFeiraRecompensaIsNotNullOrderByCriadoEmDesc().stream()
        .map(this::toAdmin)
        .toList();
  }

  /**
   * Vitrine do jogador: só o que ainda dá para pegar, mais os que ele mesmo já pegou — deixar os
   * capturados na lista é o que mostra "você já pegou este" em vez de o pedido sumir e parecer bug.
   */
  @Transactional(readOnly = true)
  public List<PedidoResponse> listarParaJogador(UUID jogadorId) {
    Set<UUID> capturados =
        capturaRepository.findByEntregadorIdOrderByCriadoEmDesc(jogadorId).stream()
            .map(FeiraCaptura::getPedidoId)
            .collect(Collectors.toSet());

    return pedidoRepository.findByFeiraRecompensaIsNotNullOrderByCriadoEmDesc().stream()
        .filter(pedido -> pedido.isFeiraCapturavel() || capturados.contains(pedido.getId()))
        .map(pedido -> toJogador(pedido, capturados.contains(pedido.getId())))
        .toList();
  }

  /**
   * A trava no pedido serializa a leitura do estoque com o decremento: sem ela, duas capturas
   * simultâneas na última unidade leem o mesmo contador e passam as duas — e falta Bis na mesa.
   */
  @Transactional
  public CapturaResponse capturar(UUID pedidoId, UUID jogadorId) {
    Pedido pedido =
        pedidoRepository
            .findByIdForUpdate(pedidoId)
            .filter(Pedido::isFeira)
            .orElseThrow(() -> new NotFoundException("PEDIDO_NOT_FOUND", "Pedido não encontrado."));

    if (pedido.getFeiraEncerradoEm() != null) {
      throw new ConflictException("PEDIDO_ENCERRADO", "Este pedido já foi encerrado.");
    }
    if (pedido.getFeiraRestantes() <= 0) {
      throw new ConflictException("RECOMPENSA_ESGOTADA", "As recompensas deste pedido acabaram.");
    }

    FeiraCaptura captura = new FeiraCaptura(UuidV7.next(), pedidoId, jogadorId);
    try {
      capturaRepository.saveAndFlush(captura);
    } catch (DataIntegrityViolationException e) {
      // ux_feira_captura: a regra "uma vez por pessoa" é do banco, não da aplicação.
      throw new ConflictException("JA_CAPTURADO", "Você já pegou este pedido.");
    }

    pedido.capturarNaFeira();

    return new CapturaResponse(
        captura.getId(),
        pedidoId,
        pedido.getFeiraRecompensa(),
        captura.getCriadoEm(),
        captura.getEntregueEm());
  }

  @Transactional(readOnly = true)
  public List<CapturaResponse> minhasCapturas(UUID jogadorId) {
    List<FeiraCaptura> capturas =
        capturaRepository.findByEntregadorIdOrderByCriadoEmDesc(jogadorId);
    Map<UUID, Pedido> pedidos =
        pedidosDe(capturas.stream().map(FeiraCaptura::getPedidoId).toList());
    return capturas.stream()
        .map(
            captura ->
                new CapturaResponse(
                    captura.getId(),
                    captura.getPedidoId(),
                    pedidos.get(captura.getPedidoId()).getFeiraRecompensa(),
                    captura.getCriadoEm(),
                    captura.getEntregueEm()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CapturaAdminResponse> capturasDoPedido(UUID pedidoId) {
    List<FeiraCaptura> capturas = capturaRepository.findByPedidoIdOrderByCriadoEmAsc(pedidoId);
    Map<UUID, Entregador> jogadores =
        entregadorRepository
            .findAllById(capturas.stream().map(FeiraCaptura::getEntregadorId).toList())
            .stream()
            .collect(Collectors.toMap(Entregador::getUsuarioId, Function.identity()));

    return capturas.stream()
        .map(
            captura -> {
              Entregador jogador = jogadores.get(captura.getEntregadorId());
              return new CapturaAdminResponse(
                  captura.getId(),
                  captura.getPedidoId(),
                  captura.getEntregadorId(),
                  jogador == null ? null : jogador.getUsuario().getNomeExibicao(),
                  jogador == null ? null : jogador.getUsername(),
                  captura.getCriadoEm(),
                  captura.getEntregueEm());
            })
        .toList();
  }

  /**
   * Entrega do prêmio físico (fluxo 5). Não mexe no estoque — ele já foi consumido na captura; isto
   * é registro de que o Bis saiu da mesa.
   */
  @Transactional
  public void marcarEntregue(UUID capturaId) {
    FeiraCaptura captura =
        capturaRepository
            .findById(capturaId)
            .orElseThrow(
                () -> new NotFoundException("CAPTURA_NOT_FOUND", "Captura não encontrada."));
    captura.marcarEntregue();
    capturaRepository.save(captura);
  }

  @Transactional
  public PedidoAdminResponse encerrar(UUID pedidoId) {
    Pedido pedido =
        pedidoRepository
            .findById(pedidoId)
            .filter(Pedido::isFeira)
            .orElseThrow(() -> new NotFoundException("PEDIDO_NOT_FOUND", "Pedido não encontrado."));
    pedido.encerrarNaFeira();
    return toAdmin(pedidoRepository.save(pedido));
  }

  private Map<UUID, Pedido> pedidosDe(List<UUID> ids) {
    return pedidoRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Pedido::getId, Function.identity()));
  }

  private PedidoResponse toJogador(Pedido pedido, boolean capturado) {
    return new PedidoResponse(
        pedido.getId(),
        pedido.getDestBairro(),
        pedido.getDestLat(),
        pedido.getDestLong(),
        pedido.getFeiraRecompensa(),
        pedido.getFeiraRestantes(),
        pedido.getFeiraEncerradoEm() != null,
        capturado);
  }

  private PedidoAdminResponse toAdmin(Pedido pedido) {
    return new PedidoAdminResponse(
        pedido.getId(),
        pedido.getNumero(),
        pedido.getDestBairro(),
        pedido.getDestLat(),
        pedido.getDestLong(),
        pedido.getFeiraRecompensa(),
        pedido.getFeiraLimite() == null ? 0 : pedido.getFeiraLimite(),
        pedido.getFeiraCapturas(),
        pedido.getFeiraRestantes(),
        pedido.getFeiraEncerradoEm(),
        pedido.getCriadoEm());
  }
}
