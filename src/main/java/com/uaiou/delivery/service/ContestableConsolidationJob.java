package com.uaiou.delivery.service;

import com.uaiou.delivery.config.DeliveryProperties;
import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-17.6 — sem disputa formal na v1 (T-17 é explícito: nenhuma rota de arbitragem), a janela de
 * contestação só existe para o estabelecimento abrir um chamado de suporte a tempo
 * (DELIVERY_CONTESTABLE, disparado na finalização). Vencida sem contestação por essa via, o pedido
 * consolida — na v1, consolidação é <strong>só mudança de estado</strong>, sem efeito financeiro (o
 * lançamento já nasceu {@code a_receber} na finalização, RF-17.5).
 */
@Component
public class ContestableConsolidationJob {

  private static final Logger log = LoggerFactory.getLogger(ContestableConsolidationJob.class);

  private final PedidoRepository pedidoRepository;
  private final DeliveryProperties properties;

  public ContestableConsolidationJob(
      PedidoRepository pedidoRepository, DeliveryProperties properties) {
    this.pedidoRepository = pedidoRepository;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "PT5M")
  @Transactional
  public void consolidarJanelasVencidas() {
    Instant limite = Instant.now().minus(properties.contestableWindow());
    List<Pedido> vencidos =
        pedidoRepository.findByStatusAndFinalizadoEmBefore(
            OrderStatus.CONTESTABLE_FINALIZED, limite);

    for (Pedido pedido : vencidos) {
      pedido.consolidarContestavelEmFinalizado();
    }
    if (!vencidos.isEmpty()) {
      log.info(
          "Consolidação contestável: {} pedido(s) movido(s) para finalizado.", vencidos.size());
    }
  }
}
