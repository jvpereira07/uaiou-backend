package com.uaiou.orders;

import java.util.UUID;

/** RF-14.4 — o estabelecimento precisa saber que há uma proposta esperando decisão. */
public record CounterofferCreatedEvent(UUID pedidoId, UUID contraofertaId, UUID entregadorId) {}
