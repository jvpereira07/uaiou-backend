package com.uaiou.orders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Destino em {@code POST /orders} (api/pedidos.md).
 *
 * <p>{@code city} é aceito porque está no contrato, mas <strong>não é persistido</strong>: V5 não
 * modelou cidade no pedido (a v1 opera numa praça só, e o endereço guardado é bairro/rua/número).
 * Aceitar e ignorar em silêncio seria pior — daí o registro explícito aqui.
 */
public record DestinationRequest(
    @NotBlank @Size(max = 120) String street,
    @NotBlank @Size(max = 10) String number,
    @Size(max = 60) String complement,
    @NotBlank @Size(max = 80) String district,
    @Size(max = 80) String city,
    BigDecimal lat,
    BigDecimal lng) {}
