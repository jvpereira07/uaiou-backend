package com.uaiou.orders.service;

import com.uaiou.orders.dto.DestinationRequest;
import java.math.BigDecimal;

/**
 * RF-11.2 — resolve o destino em coordenadas na criação do pedido. É a âncora do geofence de T-15:
 * endereço que não resolve agora vira entrega impossível de finalizar depois, então o erro precisa
 * aparecer para quem digitou, não para o entregador na porta do cliente.
 *
 * <p>Interface separada da implementação de propósito: o provedor de geocodificação está 🟡 em
 * aberto na T-11 ("Definir provedor; prever cache para endereços repetidos"). Quando for escolhido,
 * entra uma implementação nova aqui e nenhum chamador muda.
 */
public interface GeocodingService {

  /**
   * @throws com.uaiou.shared.error.BusinessRuleException 422 {@code UNGEOCODABLE_ADDRESS} quando o
   *     destino não pode ser resolvido.
   */
  Coordinates resolve(DestinationRequest destination);

  record Coordinates(BigDecimal lat, BigDecimal longitude) {}
}
