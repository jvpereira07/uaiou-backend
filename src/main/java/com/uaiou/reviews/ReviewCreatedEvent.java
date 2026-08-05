package com.uaiou.reviews;

import java.util.UUID;

/** RF-19.9 — criar avaliação enfileira recálculo do score do alvo (T-20). */
public record ReviewCreatedEvent(UUID targetId, boolean targetIsCourier) {}
