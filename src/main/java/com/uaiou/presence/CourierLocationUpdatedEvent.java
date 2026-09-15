package com.uaiou.presence;

import java.util.UUID;

/**
 * RF-26.1 — posição nova gravada. Publicado <strong>dentro</strong> da transação de {@code PUT
 * /me/location}: a detecção de chegada ao estabelecimento escreve no pedido na mesma transação, sem
 * que presença precise conhecer pedido.
 */
public record CourierLocationUpdatedEvent(UUID courierId) {}
