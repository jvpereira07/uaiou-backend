package com.uaiou.presence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Presença de um entregador elegível: quem é, onde estava e quando. É o que T-11 (motor de
 * elegibilidade) e T-15 (geofence) consomem — {@code accuracy} vai junto porque RF-10.9 exige
 * descartar leitura ruim, e essa decisão é de quem consome, não deste módulo.
 */
public record CourierPresence(
    UUID courierId, BigDecimal lat, BigDecimal longitude, BigDecimal accuracy, Instant updatedAt) {}
