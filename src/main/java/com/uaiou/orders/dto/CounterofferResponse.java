package com.uaiou.orders.dto;

import com.uaiou.counteroffers.CounterofferStatus;
import com.uaiou.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code contraoferta}, exposta ao estabelecimento (RF-14.4). {@code courierScore} nasce nulo para
 * quem não tem base ainda (RF-20.9 chega em T-20) — devolvido aqui só como o número bruto: quem
 * decide "sem base" versus "0" é a leitura do estabelecimento, T-20 formaliza o rótulo.
 */
public record CounterofferResponse(
    UUID id,
    UUID orderId,
    UUID courierId,
    String courierName,
    BigDecimal courierScore,
    Money proposedFee,
    CounterofferStatus status,
    Instant createdAt,
    Instant respondedAt) {}
