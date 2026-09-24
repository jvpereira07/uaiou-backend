package com.uaiou.feira;

import java.util.UUID;

/** Um jogador atingiu a meta de um bônus. Vira notificação depois do commit. */
public record FeiraBonusConquistadoEvent(
    UUID jogadorId, UUID bonusId, UUID conquistaId, String titulo, String recompensa, int meta) {}
