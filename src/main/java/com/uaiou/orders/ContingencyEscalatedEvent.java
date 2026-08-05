package com.uaiou.orders;

import java.time.Instant;
import java.util.UUID;

/** RF-16.3 — degrau 2: {@code delivery.code_contingency} urgente ao estabelecimento. */
public record ContingencyEscalatedEvent(UUID pedidoId, Instant prazoEm) {}
