package com.uaiou.delivery.service;

import com.uaiou.delivery.config.DeliveryProperties;
import com.uaiou.orders.Distances;
import com.uaiou.orders.entity.Pedido;
import com.uaiou.presence.service.CourierPresenceService;
import com.uaiou.users.entity.Entregador;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * RF-15.4/RF-15.5 — a decisão de habilitar a finalização é do <strong>servidor</strong>, num lugar
 * só: cliente adulterado não ganha o botão, e mudar o raio não exige atualizar o app.
 *
 * <p>Reusado por {@link com.uaiou.orders.service.DeliveryStateService} (o link só aparece dentro do
 * geofence) e {@link com.uaiou.orders.service.DeliveryCompletionService} (a mesma regra decide se a
 * finalização é aceita) — divergir entre as duas seria o link mentir sobre o que a escrita aceita.
 */
@Component
public class GeofenceEvaluator {

  private final CourierPresenceService courierPresenceService;
  private final DeliveryProperties properties;

  public GeofenceEvaluator(
      CourierPresenceService courierPresenceService, DeliveryProperties properties) {
    this.courierPresenceService = courierPresenceService;
    this.properties = properties;
  }

  public record Resultado(
      boolean inside, double radiusMeters, Double distanceMeters, String reason) {}

  /**
   * RF-15.5 — posição velha ou imprecisa não é calculada: devolve {@code inside: false} com o
   * motivo, forçando novo reporte antes da tentativa. {@code accuracy} pior que o raio do geofence
   * teria um GPS mentindo dentro da margem de erro dele mesmo.
   */
  public Resultado avaliar(Entregador entregador, Pedido pedido) {
    double raio = properties.geofenceRadiusMeters();

    if (!courierPresenceService.hasFreshPresence(entregador.getUsuarioId())
        || entregador.getLat() == null
        || entregador.getLongitude() == null) {
      return new Resultado(false, raio, null, "stale_location");
    }
    if (entregador.getAccuracy() != null && entregador.getAccuracy().doubleValue() > raio) {
      return new Resultado(false, raio, null, "imprecise_location");
    }

    double distanciaMetros =
        distanciaEmMetros(
            entregador.getLat(),
            entregador.getLongitude(),
            pedido.getDestLat(),
            pedido.getDestLong());
    return new Resultado(distanciaMetros <= raio, raio, distanciaMetros, null);
  }

  /**
   * Avaliação direta com o lat/lng do CORPO da requisição (RF-15.6/RF-17.2), não a posição salva.
   */
  public boolean dentroDoRaio(BigDecimal lat, BigDecimal lng, Pedido pedido) {
    double distanciaMetros = distanciaEmMetros(lat, lng, pedido.getDestLat(), pedido.getDestLong());
    return distanciaMetros <= properties.geofenceRadiusMeters();
  }

  public double distanciaEmMetros(
      BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
    return Distances.haversineKm(lat1, lng1, lat2, lng2) * 1000.0;
  }
}
