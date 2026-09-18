package com.uaiou.maptiles.service;

import com.uaiou.maptiles.MapTheme;
import com.uaiou.maptiles.config.MapTilesProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proxy com cache dos recursos de mapa vetorial: estilo, tiles, glifos e sprites.
 *
 * <p>Ordem de cada leitura: cache fresco → provedor → cache vencido. O último degrau é o que mantém
 * o entregador com mapa quando o provedor cai ou a cota do dia estoura — um tile de semanas atrás é
 * muito melhor que um buraco cinza no meio da rota.
 */
public class MapTileService {

  /** Cor dos prédios injetados no tema escuro: um tom acima do fundo do {@code dark-matter}. */
  private static final String DARK_BUILDINGS_COLOR = "#2b2b30";

  private final MapTilesProperties properties;
  private final GeoapifyMapClient client;
  private final MapTileStore store;
  private final MapTileSigner signer;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public MapTileService(
      MapTilesProperties properties,
      GeoapifyMapClient client,
      MapTileStore store,
      MapTileSigner signer,
      ObjectMapper objectMapper,
      Clock clock) {
    this.properties = properties;
    this.client = client;
    this.store = store;
    this.signer = signer;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public boolean isValidToken(String token) {
    return signer.isValid(token);
  }

  /**
   * Estilo pronto para o MapLibre, com todo endereço apontando para {@code publicBaseUrl}.
   *
   * @param publicBaseUrl base desta API vista pelo cliente, sem barra final.
   */
  public Optional<String> style(MapTheme theme, String publicBaseUrl) {
    String styleId = properties.styleId(theme);
    return resource("styles/" + styleId + "/style.json", "/v1/styles/" + styleId + "/style.json")
        .map(
            resource -> {
              ObjectNode style = (ObjectNode) objectMapper.readTree(decompress(resource));
              String query = "?t=" + signer.currentToken();
              String base = publicBaseUrl + "/map-tiles";
              MapStyleRewriter.Urls urls =
                  new MapStyleRewriter.Urls(
                      base + "/vector/{z}/{x}/{y}.pbf" + query,
                      base + "/fonts/" + theme.slug() + "/{fontstack}/{range}.pbf" + query,
                      base + "/sprites/" + theme.slug() + "/sprite" + query);
              String buildingsColor = theme == MapTheme.DARK ? DARK_BUILDINGS_COLOR : null;
              JsonNode rewritten =
                  MapStyleRewriter.rewrite(style, urls, properties.attribution(), buildingsColor);
              return objectMapper.writeValueAsString(rewritten);
            });
  }

  public Optional<CachedResource> vectorTile(int z, int x, int y) {
    String path = "vector/" + z + "/" + x + "/" + y + ".pbf";
    return resource(path, "/v1/tile/" + path);
  }

  /**
   * Tile raster dos mapas 2D (seletor de endereço, mapas das telas principais). Mesmo estilo do
   * tema vetorial, para o app ter uma cara só.
   */
  public Optional<CachedResource> rasterTile(MapTheme theme, int z, int x, int y, boolean retina) {
    String styleId = properties.styleId(theme);
    String file = y + (retina ? "@2x" : "") + ".png";
    return resource(
        "raster/" + styleId + "/" + z + "/" + x + "/" + file,
        "/v1/tile/" + styleId + "/" + z + "/" + x + "/" + file);
  }

  /**
   * Template no formato do Leaflet e do {@code flutter_map}: {@code {r}} vira {@code @2x} em tela
   * de alta densidade.
   */
  public String rasterUrlTemplate(MapTheme theme, String publicBaseUrl) {
    return publicBaseUrl
        + "/map-tiles/raster/"
        + theme.slug()
        + "/{z}/{x}/{y}{r}.png?t="
        + signer.currentToken();
  }

  public String attribution() {
    return properties.attribution();
  }

  public Optional<CachedResource> glyphs(MapTheme theme, String fontstack, String range) {
    String styleId = properties.styleId(theme);
    String encoded = URLEncoder.encode(fontstack, StandardCharsets.UTF_8).replace("+", "%20");
    return resource(
        "fonts/" + styleId + "/" + encoded + "/" + range + ".pbf",
        "/v1/styles/" + styleId + "/fonts/" + encoded + "/" + range + ".pbf");
  }

  /**
   * @param file {@code sprite.json}, {@code sprite.png} ou as variantes {@code @2x}.
   */
  public Optional<CachedResource> sprite(MapTheme theme, String file) {
    String styleId = properties.styleId(theme);
    return resource("sprites/" + styleId + "/" + file, "/v1/styles/" + styleId + "/" + file);
  }

  private Optional<CachedResource> resource(String cacheKey, String upstreamPath) {
    Optional<CachedResource> cached = store.find(cacheKey);
    if (cached.isPresent() && isFresh(cached.get())) {
      return cached;
    }
    Optional<CachedResource> fetched = client.fetch(upstreamPath);
    if (fetched.isPresent()) {
      store.save(cacheKey, fetched.get());
      return fetched;
    }
    return cached;
  }

  private boolean isFresh(CachedResource resource) {
    return resource.storedAt().plus(properties.cacheTtl()).isAfter(clock.instant());
  }

  /** Para quem precisa ler o corpo (o estilo) ou para cliente que não aceita gzip. */
  public static byte[] decompress(CachedResource resource) {
    if (!resource.gzip()) {
      return resource.body();
    }
    try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(resource.body()))) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Recurso de mapa com gzip corrompido", e);
    }
  }
}
