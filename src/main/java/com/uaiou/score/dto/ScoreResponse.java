package com.uaiou.score.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /me/score} — RF-20.5. {@code value} nulo é "sem base" (RF-20.9), não zero. {@code
 * penalties} só existe para o estabelecimento (RF-20.6) — nulo para entregador.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScoreResponse(
    BigDecimal value,
    String window,
    Instant calculatedAt,
    List<Component> components,
    List<Penalty> penalties) {

  public record Component(
      String name, BigDecimal value, BigDecimal weight, BigDecimal contribution) {}

  public record Penalty(String motivo, UUID pedidoId, Instant createdAt) {}
}
