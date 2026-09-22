package com.uaiou.orders.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.orders.OrderStatus;
import com.uaiou.shared.money.Money;
import com.uaiou.shared.pagination.LinkRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Uma linha de {@code GET /orders}. {@code distanceKm} é nulo para o estabelecimento (não faz
 * sentido: ele não se desloca) e derivado em runtime para o entregador (RF-11.7).
 */
public record OrderSummary(
    UUID id,
    String number,
    OrderStatus status,
    Money proposedFee,
    BigDecimal distanceKm,
    MerchantRef merchant,
    DestinationResponse destination,
    Instant createdAt,
    /**
     * RF-13.8 — âncora do "tempo decorrido desde o aceite". Devolver o instante, e não os minutos
     * já calculados, pelo mesmo motivo de {@code distanceKm}: o decorrido muda a cada segundo, e um
     * número congelado na resposta ficaria errado antes de a tela terminar de renderizar.
     */
    Instant acceptedAt,
    /** T-26 — marcos da retirada: a lista mostra "aguardando confirmação" sem abrir o detalhe. */
    Instant arrivedAt,
    Instant pickedUpAt,
    /**
     * Modo feira (docs/feira/): o prêmio em texto ("1 Bis"). Não-nulo só nos pedidos da feira, e é
     * o que o app mostra no lugar do frete — que ali é sempre zero e não significa nada.
     */
    String feiraRecompensa,
    @JsonProperty("_links") Map<String, LinkRef> links) {

  /**
   * {@code lat}/{@code lng} existem para o app desenhar a loja no mapa da entrega. No produto isso
   * saía da rota (GET .../route); no modo feira não há rota calculada — o evento cabe num salão — e
   * sem estas coordenadas o app não teria como mostrar onde é a coleta.
   */
  public record MerchantRef(UUID id, String name, String logoUrl, BigDecimal lat, BigDecimal lng) {}
}
