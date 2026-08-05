package com.uaiou.reviews.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code GET /me/reviews?direction=received} (RF-19.8). {@code summary.activeRate} existe para que
 * uma média 5,00 formada por automáticas não passe por excelência (RF-19.6/RF-19.8).
 */
public record ReceivedReviewsResponse(Summary summary, List<Entry> data) {

  public record Summary(java.math.BigDecimal average, java.math.BigDecimal activeRate, int count) {}

  public record Entry(
      UUID id,
      UUID orderId,
      String orderNumber,
      int rating,
      String comment,
      boolean active,
      Instant createdAt) {}
}
