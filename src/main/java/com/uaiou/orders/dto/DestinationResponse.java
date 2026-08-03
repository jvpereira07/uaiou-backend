package com.uaiou.orders.dto;

import java.math.BigDecimal;

/**
 * Na vitrine do entregador (RF-11.6) só o bairro é devolvido — o endereço completo é do pedido
 * aceito, não da lista pública. Por isso os campos de rua/número/coordenadas são anuláveis e somem
 * do JSON quando não se aplicam.
 */
public record DestinationResponse(
    String street,
    String number,
    String complement,
    String district,
    BigDecimal lat,
    BigDecimal lng) {

  public static DestinationResponse apenasBairro(String district) {
    return new DestinationResponse(null, null, null, district, null, null);
  }
}
