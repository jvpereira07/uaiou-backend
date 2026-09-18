package com.uaiou.maptiles.config;

import com.uaiou.maptiles.MapTheme;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Proxy de mapa vetorial do Geoapify (mapa de navegação do app).
 *
 * @param apiKey mesma conta de rotas e geocodificação — por padrão herda {@code ROUTING_API_KEY}.
 *     <strong>Vazia desliga o recurso</strong>: o estilo responde 404 e o app cai no mapa raster de
 *     reserva.
 * @param lightStyle estilo do provedor usado no tema claro. {@code maptiler-3d} é o único que já
 *     traz prédios extrudados.
 * @param darkStyle estilo do tema escuro. Os prédios 3D dele são injetados na reescrita do estilo.
 * @param cacheTtl idade a partir da qual um recurso guardado no MinIO é buscado de novo. O dado do
 *     OpenStreetMap muda devagar; o TTL só evita que uma rua nova nunca apareça.
 * @param publicBaseUrl base absoluta que o <strong>cliente</strong> usa para chegar nesta API (ex.:
 *     {@code https://api.uaiou.com.br/api/v1}). Vazia deriva da própria requisição — serve em
 *     desenvolvimento, mas atrás de proxy TLS a requisição chega como {@code http} e as URLs
 *     sairiam erradas.
 * @param signingSecret assina o token diário embutido nas URLs de tile. Sem ele os tiles seriam uma
 *     cota aberta: qualquer um baixaria o mundo por nossa conta.
 */
@ConfigurationProperties(prefix = "app.map-tiles")
public record MapTilesProperties(
    String apiKey,
    String baseUrl,
    String lightStyle,
    String darkStyle,
    Duration timeout,
    Duration cacheTtl,
    String publicBaseUrl,
    String signingSecret,
    String attribution) {

  public boolean enabled() {
    return apiKey != null && !apiKey.isBlank();
  }

  public String styleId(MapTheme theme) {
    return switch (theme) {
      case LIGHT -> lightStyle;
      case DARK -> darkStyle;
    };
  }
}
