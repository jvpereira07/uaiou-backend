package com.uaiou.maptiles.config;

import com.uaiou.maptiles.service.GeoapifyMapClient;
import com.uaiou.maptiles.service.MapTileService;
import com.uaiou.maptiles.service.MapTileSigner;
import com.uaiou.maptiles.service.MapTileStore;
import java.net.http.HttpClient;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Monta o proxy de mapa vetorial. Mesmo desenho de {@link
 * com.uaiou.routing.config.RoutingConfiguration}.
 */
@Configuration
public class MapTilesConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MapTilesConfiguration.class);

  @Bean
  public MapTileService mapTileService(
      MapTilesProperties properties, MapTileStore store, ObjectMapper objectMapper) {
    if (!properties.enabled()) {
      log.warn(
          "Mapa vetorial desligado: sem chave. GET /map-tiles/styles/* responde 404 e o app usa o"
              + " mapa raster de reserva.");
    }
    Clock clock = Clock.systemUTC();
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(properties.timeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    return new MapTileService(
        properties,
        new GeoapifyMapClient(httpClient, properties, clock),
        store,
        new MapTileSigner(properties.signingSecret(), clock),
        objectMapper,
        clock);
  }
}
