package com.uaiou.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Distância e caixa delimitadora para a elegibilidade por proximidade (RF-11.5/RF-11.7).
 *
 * <p>T-02 decidiu <strong>caixa delimitadora + distância calculada</strong> em vez de índice
 * geoespacial (ADR 0001): o banco recorta um quadrado com o índice comum de {@code (lat, long)}, e
 * o círculo do raio é aplicado depois, em memória. A caixa sozinha aceitaria os cantos do quadrado
 * — até ~41% mais longe que o raio na diagonal —, por isso os dois passos.
 */
public final class Distances {

  private static final double EARTH_RADIUS_KM = 6371.0088;
  private static final double KM_POR_GRAU_DE_LATITUDE = 111.32;

  private Distances() {}

  /** Haversine — precisão de sobra para raios de poucos km, sem dependência externa. */
  public static double haversineKm(
      BigDecimal lat1, BigDecimal long1, BigDecimal lat2, BigDecimal long2) {
    double phi1 = Math.toRadians(lat1.doubleValue());
    double phi2 = Math.toRadians(lat2.doubleValue());
    double deltaPhi = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
    double deltaLambda = Math.toRadians(long2.doubleValue() - long1.doubleValue());

    double a =
        Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
            + Math.cos(phi1)
                * Math.cos(phi2)
                * Math.sin(deltaLambda / 2)
                * Math.sin(deltaLambda / 2);
    return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
  }

  /**
   * Exposto na resposta com uma casa decimal — precisão maior seria falsa (o GPS tem erro próprio).
   */
  public static BigDecimal arredondarKm(double km) {
    return BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP);
  }

  /**
   * Caixa que contém o círculo de raio {@code raioKm} ao redor do ponto. O grau de longitude
   * encolhe conforme se afasta do equador ({@code cos(latitude)}), então a largura precisa ser
   * corrigida — sem isso a caixa ficaria estreita demais e perderia pedidos elegíveis a
   * leste/oeste.
   */
  public static BoundingBox caixaDelimitadora(BigDecimal lat, BigDecimal longitude, double raioKm) {
    double deltaLat = raioKm / KM_POR_GRAU_DE_LATITUDE;
    double cosLat = Math.cos(Math.toRadians(lat.doubleValue()));
    // Perto dos polos cos(lat) tende a zero e a correção explodiria; o piso mantém a caixa finita.
    double kmPorGrauDeLongitude = Math.max(KM_POR_GRAU_DE_LATITUDE * cosLat, 0.1);
    double deltaLong = raioKm / kmPorGrauDeLongitude;

    return new BoundingBox(
        lat.subtract(BigDecimal.valueOf(deltaLat)),
        lat.add(BigDecimal.valueOf(deltaLat)),
        longitude.subtract(BigDecimal.valueOf(deltaLong)),
        longitude.add(BigDecimal.valueOf(deltaLong)));
  }

  public record BoundingBox(
      BigDecimal latMin, BigDecimal latMax, BigDecimal longMin, BigDecimal longMax) {}
}
