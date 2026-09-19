package com.uaiou.orders.timeout;

import com.uaiou.orders.OrderLifecycleEvents.InterventionOrigin;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.orders.service.PlatformInterventionService;
import com.uaiou.shared.id.UuidV7;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aplica as regras de timeout ligadas. Cada pedido vai numa transação própria: um pedido que falhe
 * (constraint, corrida) não pode reverter os outros nem travar a regra inteira no próximo ciclo.
 *
 * <p>A seleção por id é só candidata — a decisão acontece depois do lock, relendo status e marco: o
 * entregador pode ter coletado no mesmo instante em que o job olhou.
 */
@Service
public class OrderTimeoutService {

  private static final Logger log = LoggerFactory.getLogger(OrderTimeoutService.class);

  private final ConfiguracaoTimeoutRepository configuracaoRepository;
  private final OcorrenciaTimeoutRepository ocorrenciaRepository;
  private final PedidoRepository pedidoRepository;
  private final PlatformInterventionService interventionService;
  private final TransactionTemplate transactionTemplate;

  public OrderTimeoutService(
      ConfiguracaoTimeoutRepository configuracaoRepository,
      OcorrenciaTimeoutRepository ocorrenciaRepository,
      PedidoRepository pedidoRepository,
      PlatformInterventionService interventionService,
      PlatformTransactionManager transactionManager) {
    this.configuracaoRepository = configuracaoRepository;
    this.ocorrenciaRepository = ocorrenciaRepository;
    this.pedidoRepository = pedidoRepository;
    this.interventionService = interventionService;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /**
   * @return quantos pedidos cada regra ligada afetou neste ciclo.
   */
  public Map<OrderTimeoutRule, Integer> aplicarRegrasAtivas() {
    Map<OrderTimeoutRule, Integer> afetados = new EnumMap<>(OrderTimeoutRule.class);
    Instant agora = Instant.now();
    for (ConfiguracaoTimeout config : configuracaoRepository.findByAtivoTrue()) {
      OrderTimeoutRule regra = config.getRegra();
      Instant limite = agora.minus(config.getDuracao());
      int total = 0;
      for (UUID pedidoId : candidatos(regra, limite)) {
        try {
          Boolean aplicado =
              transactionTemplate.execute(status -> aplicarUm(regra, pedidoId, limite));
          if (Boolean.TRUE.equals(aplicado)) {
            total++;
          }
        } catch (RuntimeException e) {
          log.warn("Timeout {}: falha no pedido {}.", regra.code(), pedidoId, e);
        }
      }
      afetados.put(regra, total);
      if (total > 0) {
        log.info("Timeout {}: {} pedido(s) afetado(s).", regra.code(), total);
      }
    }
    return afetados;
  }

  /** Quantos pedidos a regra alcançaria agora com o prazo dado — ligada ou não. */
  public int contarVencidos(OrderTimeoutRule regra, Instant limite) {
    return candidatos(regra, limite).size();
  }

  public List<UUID> candidatos(OrderTimeoutRule regra, Instant limite) {
    return switch (regra) {
      case UNACCEPTED -> pedidoRepository.idsCriadosAntesDe(regra.statuses(), limite);
      case NOT_PICKED_UP -> pedidoRepository.idsAceitosAntesDe(regra.statuses(), limite);
      case NOT_DELIVERED ->
          pedidoRepository.idsColetadosAntesDeSemOcorrencia(
              regra.statuses(), limite, regra.dbKey());
    };
  }

  private boolean aplicarUm(OrderTimeoutRule regra, UUID pedidoId, Instant limite) {
    Pedido pedido = pedidoRepository.findByIdForUpdate(pedidoId).orElse(null);
    if (pedido == null || !regra.statuses().contains(pedido.getStatus())) {
      return false;
    }
    Instant marco = marco(regra, pedido);
    if (marco == null || !marco.isBefore(limite)) {
      return false;
    }

    OrderStatus anterior = pedido.getStatus();
    switch (regra.action()) {
      case CANCEL ->
          interventionService.cancelar(
              pedido,
              InterventionOrigin.TIMEOUT,
              "Sem aceite dentro do prazo configurado (" + regra.code() + ").");
      case RETURN_TO_SHOWCASE ->
          interventionService.devolverAVitrine(pedido, InterventionOrigin.TIMEOUT);
      case FLAG -> {
        // Só registra: a ocorrência é o sinal que o painel mostra.
      }
    }
    ocorrenciaRepository.save(new OcorrenciaTimeout(UuidV7.next(), pedidoId, regra, anterior));
    return true;
  }

  private static Instant marco(OrderTimeoutRule regra, Pedido pedido) {
    return switch (regra) {
      case UNACCEPTED -> pedido.getCriadoEm();
      case NOT_PICKED_UP -> pedido.getAceitoEm();
      case NOT_DELIVERED -> pedido.getColetadoEm();
    };
  }
}
