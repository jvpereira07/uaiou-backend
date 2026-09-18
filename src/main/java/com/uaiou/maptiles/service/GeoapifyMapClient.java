package com.uaiou.maptiles.service;

import com.uaiou.maptiles.config.MapTilesProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * Busca recursos de mapa em {@code maps.geoapify.com}, pela mesma chave de rotas.
 *
 * <p>{@link HttpClient} do JDK, e não o {@code RestClient} dos outros provedores: aqui o corpo é
 * binário e precisa chegar <strong>ainda comprimido</strong> para ser guardado e repassado como
 * veio. O cliente do JDK nunca descomprime por conta própria, então o gzip pedido é o gzip
 * recebido.
 *
 * <p>Nenhum método lança: provedor fora, cota estourada ou tempo esgotado são "não tenho", e quem
 * chama decide entre servir o que já estava no cache ou responder erro.
 */
public class GeoapifyMapClient {

  private static final Logger log = LoggerFactory.getLogger(GeoapifyMapClient.class);

  private final HttpClient httpClient;
  private final MapTilesProperties properties;
  private final Clock clock;

  public GeoapifyMapClient(HttpClient httpClient, MapTilesProperties properties, Clock clock) {
    this.httpClient = httpClient;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * @param path caminho já codificado, sem a chave — ex. {@code /v1/tile/vector/14/1/2.pbf}.
   */
  public Optional<CachedResource> fetch(String path) {
    URI uri = URI.create(properties.baseUrl() + path + "?apiKey=" + properties.apiKey());
    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(properties.timeout())
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
            .GET()
            .build();
    try {
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      int status = response.statusCode();
      if (status != 200 && status != 204) {
        // O caminho vai no log; a URI inteira não — ela carrega a chave.
        log.warn("Provedor de mapa respondeu {} para {}", status, path);
        return Optional.empty();
      }
      byte[] body = status == 204 ? new byte[0] : response.body();
      String contentType =
          response
              .headers()
              .firstValue(HttpHeaders.CONTENT_TYPE)
              .orElse("application/octet-stream");
      boolean gzip =
          body.length > 0
              && response
                  .headers()
                  .firstValue(HttpHeaders.CONTENT_ENCODING)
                  .map("gzip"::equalsIgnoreCase)
                  .orElse(false);
      return Optional.of(new CachedResource(body, contentType, gzip, clock.instant()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (Exception e) {
      log.warn("Provedor de mapa indisponível para {}: {}", path, e.toString());
      return Optional.empty();
    }
  }
}
