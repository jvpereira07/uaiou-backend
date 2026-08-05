package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.delivery.DeliveryCodeStatus;
import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.pagination.LinkRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GET /orders/{id}/delivery} (api/entregas.md) — RF-15.3. Rota de polling: enxuta, sem
 * escrita, chamada em loop enquanto o entregador se aproxima.
 *
 * <p>{@code _links.completion} só aparece dentro do geofence (RF-15.4/RN-08.1): o servidor decide a
 * habilitação, o app só lê o que pode fazer.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryStateResponse(
    UUID orderId,
    OrderStatus status,
    Geofence geofence,
    DeliveryCode deliveryCode,
    Contingency contingency,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  public record Geofence(
      boolean inside, double radiusMeters, Double distanceMeters, String reason) {}

  public record DeliveryCode(DeliveryCodeStatus status, int attemptsLeft, List<String> channels) {}

  public record Contingency(
      Integer step, boolean contestableReleased, Instant merchantDeadlineAt) {}
}
