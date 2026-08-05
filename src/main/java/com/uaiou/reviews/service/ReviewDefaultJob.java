package com.uaiou.reviews.service;

import com.uaiou.orders.OrderStatus;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.orders.repository.PedidoRepository;
import com.uaiou.reviews.ReviewCreatedEvent;
import com.uaiou.reviews.config.ReviewsProperties;
import com.uaiou.reviews.entity.Avaliacao;
import com.uaiou.reviews.repository.AvaliacaoRepository;
import com.uaiou.shared.id.UuidV7;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-19.5 — padrão positivo: ao vencer o prazo, o lado que não avaliou recebe uma avaliação {@code
 * ativa = false} criada em nome dele sobre a contraparte, fechando o ciclo sem forçar opinião.
 * RF-19.6: só isso permite {@code activeRate} existir — sem a marca, essa automática pareceria uma
 * nota real.
 */
@Component
public class ReviewDefaultJob {

  private static final Logger log = LoggerFactory.getLogger(ReviewDefaultJob.class);
  private static final List<OrderStatus> FINALIZADOS =
      List.of(OrderStatus.FINALIZED, OrderStatus.CONTESTABLE_FINALIZED);

  private final PedidoRepository pedidoRepository;
  private final AvaliacaoRepository avaliacaoRepository;
  private final ReviewsProperties properties;
  private final ApplicationEventPublisher events;

  public ReviewDefaultJob(
      PedidoRepository pedidoRepository,
      AvaliacaoRepository avaliacaoRepository,
      ReviewsProperties properties,
      ApplicationEventPublisher events) {
    this.pedidoRepository = pedidoRepository;
    this.avaliacaoRepository = avaliacaoRepository;
    this.properties = properties;
    this.events = events;
  }

  @Scheduled(fixedDelayString = "PT10M")
  @Transactional
  public void fecharCiclosVencidos() {
    Instant limite = Instant.now().minus(properties.window());
    List<Pedido> vencidos =
        pedidoRepository.findByStatusInAndFinalizadoEmBefore(FINALIZADOS, limite);

    int criadas = 0;
    for (Pedido pedido : vencidos) {
      criadas += fecharUm(pedido);
    }
    if (criadas > 0) {
      log.info("Avaliações automáticas (padrão positivo) criadas: {}.", criadas);
    }
  }

  private int fecharUm(Pedido pedido) {
    int criadas = 0;
    if (!avaliacaoRepository.existsByPedidoIdAndAutorId(
        pedido.getId(), pedido.getEstabelecimentoId())) {
      avaliacaoRepository.save(
          Avaliacao.padraoPositivo(
              UuidV7.next(),
              pedido.getId(),
              pedido.getEstabelecimentoId(),
              pedido.getEntregadorId()));
      events.publishEvent(new ReviewCreatedEvent(pedido.getEntregadorId(), true));
      criadas++;
    }
    if (!avaliacaoRepository.existsByPedidoIdAndAutorId(pedido.getId(), pedido.getEntregadorId())) {
      avaliacaoRepository.save(
          Avaliacao.padraoPositivo(
              UuidV7.next(),
              pedido.getId(),
              pedido.getEntregadorId(),
              pedido.getEstabelecimentoId()));
      events.publishEvent(new ReviewCreatedEvent(pedido.getEstabelecimentoId(), false));
      criadas++;
    }
    return criadas;
  }
}
