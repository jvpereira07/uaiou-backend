package com.uaiou.delivery.service;

/**
 * RN-08.6 — um dos dois canais de despacho do código de entrega (o outro é a leitura auditada pelo
 * estabelecimento, {@code GET /orders/{id}/delivery/code}).
 *
 * <p>Provedor de SMS é risco 🔴 aberto na documentação (T-15/T-16): sem credencial real neste
 * ambiente. Mesmo seam de {@code PushSender} (T-08) e {@code GeocodingService} (T-11) — interface +
 * implementação padrão que registra, para o provedor real entrar depois sem tocar nos chamadores.
 */
public interface SmsSender {

  void send(String phoneE164, String message);
}
