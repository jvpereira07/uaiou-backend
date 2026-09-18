package com.uaiou.maptiles.web;

import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.maptiles.MapTheme;
import com.uaiou.maptiles.config.MapTilesProperties;
import com.uaiou.maptiles.service.CachedResource;
import com.uaiou.maptiles.service.MapTileService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.error.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Mapa vetorial do app (tela de navegação), servido por este backend no lugar do Geoapify.
 *
 * <ul>
 *   <li>{@code GET /map-tiles/styles/{light|dark}} — exige sessão; devolve o estilo com as URLs
 *       abaixo já assinadas.
 *   <li>{@code GET /map-tiles/vector/{z}/{x}/{y}.pbf}, {@code /fonts/...}, {@code /sprites/...} —
 *       quem busca é o MapLibre, sem o header de sessão; a autorização é o token {@code t} que veio
 *       no estilo (ver {@link com.uaiou.maptiles.service.MapTileSigner}).
 * </ul>
 */
@RestController
@RequestMapping("/map-tiles")
public class MapTilesController {

  private static final Pattern FONTSTACK = Pattern.compile("[\\p{L}\\p{N} ,_-]{1,200}");
  private static final Pattern GLYPH_RANGE = Pattern.compile("(\\d{1,5})-(\\d{1,5})");
  private static final Pattern RASTER_FILE = Pattern.compile("(\\d{1,7})(@2x)?\\.png");
  private static final int VECTOR_MAX_ZOOM = 14;

  /** Último zoom do raster do provedor. */
  private static final int RASTER_MAX_ZOOM = 20;

  private static final Set<String> SPRITE_FILES =
      Set.of("sprite.json", "sprite.png", "sprite@2x.json", "sprite@2x.png");

  /** O tile de um endereço só muda quando o provedor atualiza o dado; o aparelho pode guardar. */
  private static final CacheControl IMMUTABLE_ENOUGH =
      CacheControl.maxAge(Duration.ofDays(7)).cachePublic();

  private final MapTileService mapTileService;
  private final MapTilesProperties properties;
  private final CurrentUserHolder currentUserHolder;

  public MapTilesController(
      MapTileService mapTileService,
      MapTilesProperties properties,
      CurrentUserHolder currentUserHolder) {
    this.mapTileService = mapTileService;
    this.properties = properties;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping("/styles/{theme}")
  public ResponseEntity<String> style(@PathVariable String theme) {
    currentUserHolder.require();
    MapTheme mapTheme = theme(theme);
    requireEnabled();

    return mapTileService
        .style(mapTheme, publicBaseUrl())
        .map(
            json ->
                ResponseEntity.ok()
                    // Carrega o token do dia: não pode parar em cache compartilhado.
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
  }

  @GetMapping("/vector/{z}/{x}/{y}.pbf")
  public ResponseEntity<byte[]> vectorTile(
      @PathVariable int z,
      @PathVariable int x,
      @PathVariable int y,
      @RequestParam(name = "t", required = false) String token,
      HttpServletRequest request) {
    requireToken(token);
    // Acima de 14 o provedor não tem dado: o estilo declara maxzoom 14 e o MapLibre amplia sozinho.
    // Pedido fora da grade é erro de quem chamou, não motivo para gastar cota.
    if (!insideGrid(z, x, y, VECTOR_MAX_ZOOM)) {
      throw new NotFoundException("MAP_TILE_NOT_FOUND", "Tile fora da grade do mapa.");
    }
    return serve(mapTileService.vectorTile(z, x, y), request);
  }

  /**
   * Configuração da camada raster dos mapas 2D. Exige sessão pelo mesmo motivo do estilo: é daqui
   * que sai o token.
   */
  @GetMapping("/raster/{theme}")
  public ResponseEntity<RasterLayerResponse> rasterLayer(@PathVariable String theme) {
    currentUserHolder.require();
    MapTheme mapTheme = theme(theme);
    requireEnabled();

    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            new RasterLayerResponse(
                mapTileService.rasterUrlTemplate(mapTheme, publicBaseUrl()),
                RASTER_MAX_ZOOM,
                mapTileService.attribution()));
  }

  @GetMapping("/raster/{theme}/{z}/{x}/{file}")
  public ResponseEntity<byte[]> rasterTile(
      @PathVariable String theme,
      @PathVariable int z,
      @PathVariable int x,
      @PathVariable String file,
      @RequestParam(name = "t", required = false) String token,
      HttpServletRequest request) {
    requireToken(token);
    MapTheme mapTheme = theme(theme);
    Matcher matcher = RASTER_FILE.matcher(file);
    if (!matcher.matches()) {
      throw new NotFoundException("MAP_TILE_NOT_FOUND", "Tile fora da grade do mapa.");
    }
    int y = Integer.parseInt(matcher.group(1));
    if (!insideGrid(z, x, y, RASTER_MAX_ZOOM)) {
      throw new NotFoundException("MAP_TILE_NOT_FOUND", "Tile fora da grade do mapa.");
    }
    boolean retina = matcher.group(2) != null;
    return serve(mapTileService.rasterTile(mapTheme, z, x, y, retina), request);
  }

  @GetMapping("/fonts/{theme}/{fontstack}/{range}.pbf")
  public ResponseEntity<byte[]> glyphs(
      @PathVariable String theme,
      @PathVariable String fontstack,
      @PathVariable String range,
      @RequestParam(name = "t", required = false) String token,
      HttpServletRequest request) {
    requireToken(token);
    MapTheme mapTheme = theme(theme);
    if (!FONTSTACK.matcher(fontstack).matches() || !validRange(range)) {
      throw new NotFoundException("MAP_GLYPHS_NOT_FOUND", "Fonte ou faixa de glifos inválida.");
    }
    return serve(mapTileService.glyphs(mapTheme, fontstack, range), request);
  }

  @GetMapping("/sprites/{theme}/{file}")
  public ResponseEntity<byte[]> sprite(
      @PathVariable String theme,
      @PathVariable String file,
      @RequestParam(name = "t", required = false) String token,
      HttpServletRequest request) {
    requireToken(token);
    MapTheme mapTheme = theme(theme);
    if (!SPRITE_FILES.contains(file)) {
      throw new NotFoundException("MAP_SPRITE_NOT_FOUND", "Sprite inexistente.");
    }
    return serve(mapTileService.sprite(mapTheme, file), request);
  }

  /**
   * Repassa o corpo como foi guardado. Gzip sai comprimido para quem aceita (todo cliente do
   * MapLibre) e descomprimido só para quem não aceita.
   */
  private ResponseEntity<byte[]> serve(Optional<CachedResource> found, HttpServletRequest request) {
    if (found.isEmpty()) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }
    CachedResource resource = found.get();
    if (resource.empty()) {
      // 204 é como o MapLibre entende "tile sem nada desenhado" — e o provedor responde igual.
      return ResponseEntity.noContent().cacheControl(IMMUTABLE_ENOUGH).build();
    }

    ResponseEntity.BodyBuilder response =
        ResponseEntity.ok()
            .cacheControl(IMMUTABLE_ENOUGH)
            .header(HttpHeaders.CONTENT_TYPE, resource.contentType())
            .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING);
    String acceptEncoding = request.getHeader(HttpHeaders.ACCEPT_ENCODING);
    if (resource.gzip() && acceptEncoding != null && acceptEncoding.contains("gzip")) {
      return response.header(HttpHeaders.CONTENT_ENCODING, "gzip").body(resource.body());
    }
    return response.body(MapTileService.decompress(resource));
  }

  private void requireToken(String token) {
    requireEnabled();
    if (!mapTileService.isValidToken(token)) {
      throw new ForbiddenException(
          "MAP_TILE_TOKEN_INVALID", "Token do mapa ausente ou vencido. Recarregue o estilo.");
    }
  }

  private void requireEnabled() {
    if (!properties.enabled()) {
      throw new NotFoundException("MAP_TILES_DISABLED", "Mapa vetorial desligado neste ambiente.");
    }
  }

  private static boolean insideGrid(int z, int x, int y, int maxZoom) {
    return z >= 0 && z <= maxZoom && x >= 0 && y >= 0 && x < (1 << z) && y < (1 << z);
  }

  private MapTheme theme(String slug) {
    return MapTheme.fromSlug(slug)
        .orElseThrow(
            () -> new NotFoundException("MAP_THEME_NOT_FOUND", "Tema de mapa inexistente."));
  }

  /** Faixa de glifos do MapLibre: blocos de 256 códigos, {@code 0-255}, {@code 256-511}... */
  private boolean validRange(String range) {
    var matcher = GLYPH_RANGE.matcher(range);
    if (!matcher.matches()) {
      return false;
    }
    int start = Integer.parseInt(matcher.group(1));
    int end = Integer.parseInt(matcher.group(2));
    return start % 256 == 0 && end == start + 255 && end <= 65535;
  }

  private String publicBaseUrl() {
    String configured = properties.publicBaseUrl();
    if (configured != null && !configured.isBlank()) {
      return configured.endsWith("/")
          ? configured.substring(0, configured.length() - 1)
          : configured;
    }
    return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
  }
}
