package com.uaiou.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** {@code GET /me/stats/series} (RF-22.6) — dia sem atividade retorna 0, nunca é omitido. */
public record SeriesResponse(String metric, String granularity, List<Point> data) {

  public record Point(LocalDate date, BigDecimal value) {}
}
