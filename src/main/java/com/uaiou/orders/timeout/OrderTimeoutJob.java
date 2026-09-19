package com.uaiou.orders.timeout;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Ciclo das regras de timeout (V26). Regra desligada não custa nada além de uma leitura. */
@Component
public class OrderTimeoutJob {

  private final OrderTimeoutService orderTimeoutService;

  public OrderTimeoutJob(OrderTimeoutService orderTimeoutService) {
    this.orderTimeoutService = orderTimeoutService;
  }

  @Scheduled(fixedDelayString = "PT1M")
  public void executar() {
    orderTimeoutService.aplicarRegrasAtivas();
  }
}
