package com.uaiou.feira.service;

import com.uaiou.feira.FeiraBonusConquistadoEvent;
import com.uaiou.feira.dto.FeiraDtos.BonusAdminResponse;
import com.uaiou.feira.dto.FeiraDtos.BonusConquistaAdminResponse;
import com.uaiou.feira.dto.FeiraDtos.BonusResponse;
import com.uaiou.feira.dto.FeiraDtos.CriarBonusRequest;
import com.uaiou.feira.entity.FeiraBonus;
import com.uaiou.feira.entity.FeiraBonusConquista;
import com.uaiou.feira.repository.FeiraBonusConquistaRepository;
import com.uaiou.feira.repository.FeiraBonusRepository;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.repository.PedidoRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bônus por meta da feira (V32): "complete N entregas e ganhe X". A conquista é avaliada a cada
 * entrega finalizada; o prêmio sai no balcão, pelo operador, como o dos pedidos.
 */
@Service
public class FeiraBonusService {

  /** Contam como entrega concluída — mesmo critério da situação "entregue" no painel. */
  private static final List<OrderStatus> CONCLUIDAS =
      List.of(OrderStatus.FINALIZED, OrderStatus.CONTESTABLE_FINALIZED);

  private final FeiraBonusRepository bonusRepository;
  private final FeiraBonusConquistaRepository conquistaRepository;
  private final PedidoRepository pedidoRepository;
  private final EntregadorRepository entregadorRepository;
  private final ApplicationEventPublisher events;

  public FeiraBonusService(
      FeiraBonusRepository bonusRepository,
      FeiraBonusConquistaRepository conquistaRepository,
      PedidoRepository pedidoRepository,
      EntregadorRepository entregadorRepository,
      ApplicationEventPublisher events) {
    this.bonusRepository = bonusRepository;
    this.conquistaRepository = conquistaRepository;
    this.pedidoRepository = pedidoRepository;
    this.entregadorRepository = entregadorRepository;
    this.events = events;
  }

  @Transactional
  public BonusAdminResponse criar(CriarBonusRequest request) {
    FeiraBonus bonus =
        bonusRepository.saveAndFlush(
            new FeiraBonus(
                UuidV7.next(),
                request.titulo().trim(),
                request.meta(),
                request.recompensa().trim()));
    return toAdmin(bonus, List.of());
  }

  @Transactional(readOnly = true)
  public List<BonusAdminResponse> listarParaAdmin() {
    Map<UUID, List<FeiraBonusConquista>> porBonus =
        conquistaRepository.findAll().stream()
            .collect(Collectors.groupingBy(FeiraBonusConquista::getBonusId));
    return bonusRepository.findAllByOrderByMetaAscCriadoEmAsc().stream()
        .map(bonus -> toAdmin(bonus, porBonus.getOrDefault(bonus.getId(), List.of())))
        .toList();
  }

  /** Todas as conquistas, a mais antiga primeiro — é a fila do balcão para os bônus. */
  @Transactional(readOnly = true)
  public List<BonusConquistaAdminResponse> conquistasParaAdmin() {
    List<FeiraBonusConquista> conquistas = conquistaRepository.findAllByOrderByConquistadoEmAsc();
    Map<UUID, FeiraBonus> bonus =
        bonusRepository
            .findAllById(conquistas.stream().map(FeiraBonusConquista::getBonusId).toList())
            .stream()
            .collect(Collectors.toMap(FeiraBonus::getId, Function.identity()));
    Map<UUID, Entregador> jogadores =
        entregadorRepository
            .findAllById(conquistas.stream().map(FeiraBonusConquista::getEntregadorId).toList())
            .stream()
            .collect(Collectors.toMap(Entregador::getUsuarioId, Function.identity()));

    return conquistas.stream()
        .map(
            conquista -> {
              FeiraBonus b = bonus.get(conquista.getBonusId());
              Entregador jogador = jogadores.get(conquista.getEntregadorId());
              return new BonusConquistaAdminResponse(
                  conquista.getId(),
                  conquista.getBonusId(),
                  b == null ? null : b.getTitulo(),
                  b == null ? null : b.getRecompensa(),
                  conquista.getEntregadorId(),
                  jogador == null ? null : jogador.getUsuario().getNomeExibicao(),
                  jogador == null ? null : jogador.getUsername(),
                  conquista.getConquistadoEm(),
                  conquista.getEntregueEm());
            })
        .toList();
  }

  /** Para de conceder; quem já conquistou continua podendo retirar. */
  @Transactional
  public BonusAdminResponse encerrar(UUID bonusId) {
    FeiraBonus bonus =
        bonusRepository
            .findById(bonusId)
            .orElseThrow(() -> new NotFoundException("BONUS_NOT_FOUND", "Bônus não encontrado."));
    bonus.encerrar();
    return toAdmin(bonus, conquistaRepository.findByBonusIdOrderByConquistadoEmAsc(bonusId));
  }

  /** Balcão: o jogador retirou o prêmio do bônus. */
  @Transactional
  public void marcarEntregue(UUID conquistaId) {
    conquistaRepository
        .findById(conquistaId)
        .orElseThrow(
            () -> new NotFoundException("CONQUISTA_NOT_FOUND", "Conquista não encontrada."))
        .marcarEntregue();
  }

  @Transactional(readOnly = true)
  public List<BonusResponse> listarParaJogador(UUID jogadorId) {
    int entregas = (int) pedidoRepository.contarEntregasDeFeira(jogadorId, CONCLUIDAS);
    Map<UUID, FeiraBonusConquista> minhas =
        conquistaRepository.findByEntregadorId(jogadorId).stream()
            .collect(Collectors.toMap(FeiraBonusConquista::getBonusId, Function.identity()));

    return bonusRepository.findAllByOrderByMetaAscCriadoEmAsc().stream()
        // Encerrado e não conquistado não tem mais o que mostrar ao jogador.
        .filter(bonus -> bonus.isAtivo() || minhas.containsKey(bonus.getId()))
        .map(
            bonus -> {
              FeiraBonusConquista conquista = minhas.get(bonus.getId());
              return new BonusResponse(
                  bonus.getId(),
                  bonus.getTitulo(),
                  bonus.getMeta(),
                  bonus.getRecompensa(),
                  entregas,
                  !bonus.isAtivo(),
                  conquista != null,
                  conquista == null ? null : conquista.getConquistadoEm(),
                  conquista == null ? null : conquista.getEntregueEm());
            })
        .toList();
  }

  /**
   * Chamado na transação da finalização, depois de o pedido virar FINALIZED: concede todo bônus
   * ativo cuja meta o jogador alcançou e ainda não tinha. A unique da conquista é a rede embaixo
   * contra concessão em dobro.
   */
  @Transactional
  public void avaliar(UUID jogadorId) {
    // O pedido recém-finalizado pode ainda não ter ido ao banco; o flush garante que ele conte.
    pedidoRepository.flush();
    long entregas = pedidoRepository.contarEntregasDeFeira(jogadorId, CONCLUIDAS);
    Set<UUID> jaConquistados =
        conquistaRepository.findByEntregadorId(jogadorId).stream()
            .map(FeiraBonusConquista::getBonusId)
            .collect(Collectors.toSet());

    for (FeiraBonus bonus : bonusRepository.findAllByOrderByMetaAscCriadoEmAsc()) {
      if (!bonus.isAtivo()
          || bonus.getMeta() > entregas
          || jaConquistados.contains(bonus.getId())) {
        continue;
      }
      FeiraBonusConquista conquista =
          conquistaRepository.save(
              new FeiraBonusConquista(UuidV7.next(), bonus.getId(), jogadorId));
      events.publishEvent(
          new FeiraBonusConquistadoEvent(
              jogadorId,
              bonus.getId(),
              conquista.getId(),
              bonus.getTitulo(),
              bonus.getRecompensa(),
              bonus.getMeta()));
    }
  }

  private static BonusAdminResponse toAdmin(
      FeiraBonus bonus, List<FeiraBonusConquista> conquistas) {
    return new BonusAdminResponse(
        bonus.getId(),
        bonus.getTitulo(),
        bonus.getMeta(),
        bonus.getRecompensa(),
        conquistas.size(),
        (int) conquistas.stream().filter(c -> c.getEntregueEm() != null).count(),
        bonus.getEncerradoEm(),
        bonus.getCriadoEm());
  }
}
