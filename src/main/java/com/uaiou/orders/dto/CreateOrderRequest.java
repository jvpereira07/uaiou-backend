package com.uaiou.orders.dto;

import com.uaiou.shared.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code POST /orders} (api/pedidos.md). */
public record CreateOrderRequest(
    @NotNull Money proposedFee,
    Instant expectedDeliveryAt,
    @NotNull @Valid DestinationRequest destination,
    @NotNull @Valid ReceiverRequest receiver) {

  public record ReceiverRequest(
      @NotBlank @Size(max = 120) String name, @Size(max = 15) String phone) {}
}
