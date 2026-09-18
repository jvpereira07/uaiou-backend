package com.uaiou.maptiles.service;

import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Troca, no estilo do provedor, todo endereço de {@code maps.geoapify.com} por um endereço deste
 * backend. O estilo original aponta para o provedor com a chave na query — repassá-lo cru seria
 * entregar a chave da conta a cada app que abrisse o mapa.
 *
 * <p>Os dois estilos escolhidos usam a mesma fonte vetorial (OpenMapTiles, zoom até 14 — conferido
 * no {@code data.json} de cada um), por isso toda fonte vetorial vira a mesma URL de tile: o tile
 * baixado no tema claro serve ao escuro.
 */
public final class MapStyleRewriter {

  /** Último zoom com dado na fonte do provedor; acima disso o MapLibre amplia o tile 14. */
  static final int VECTOR_MAX_ZOOM = 14;

  private static final String BUILDINGS_3D_LAYER = "building-3d";

  private MapStyleRewriter() {}

  /**
   * @param urls endereços públicos, já com o token na query.
   * @param buildings3dColor cor dos prédios a extrudar quando o estilo não traz camada 3D; {@code
   *     null} deixa o estilo como está.
   */
  public static ObjectNode rewrite(
      ObjectNode style, Urls urls, String attribution, String buildings3dColor) {
    ObjectNode sources = (ObjectNode) style.path("sources");
    for (Map.Entry<String, JsonNode> entry : sources.properties()) {
      if (!"vector".equals(entry.getValue().path("type").asString(""))) {
        continue;
      }
      ObjectNode source = style.objectNode();
      source.put("type", "vector");
      source.putArray("tiles").add(urls.vectorTiles());
      source.put("minzoom", 0);
      source.put("maxzoom", VECTOR_MAX_ZOOM);
      source.put("attribution", attribution);
      sources.set(entry.getKey(), source);
    }

    style.put("glyphs", urls.glyphs());
    if (style.hasNonNull("sprite")) {
      style.put("sprite", urls.sprite());
    }
    if (buildings3dColor != null) {
      ensureBuildings3d(style, buildings3dColor);
    }
    return style;
  }

  /**
   * O estilo escuro não tem prédios em pé — sem isto, a câmera inclinada à noite mostraria uma
   * cidade chapada e de dia uma cidade em 3D. A camada entra logo acima do último desenho de
   * prédio, para ficar sob ruas e nomes.
   */
  private static void ensureBuildings3d(ObjectNode style, String color) {
    ArrayNode layers = (ArrayNode) style.path("layers");
    int insertAt = -1;
    String source = null;
    for (int i = 0; i < layers.size(); i++) {
      JsonNode layer = layers.get(i);
      if ("fill-extrusion".equals(layer.path("type").asString(""))) {
        return;
      }
      if ("building".equals(layer.path("source-layer").asString(""))) {
        insertAt = i + 1;
        source = layer.path("source").asString();
      }
    }
    if (insertAt < 0) {
      return;
    }

    ObjectNode layer = style.objectNode();
    layer.put("id", BUILDINGS_3D_LAYER);
    layer.put("type", "fill-extrusion");
    layer.put("source", source);
    layer.put("source-layer", "building");
    layer.put("minzoom", VECTOR_MAX_ZOOM);
    ArrayNode filter = layer.putArray("filter");
    filter.add("!has").add("hide_3d");
    ObjectNode paint = layer.putObject("paint");
    paint.put("fill-extrusion-color", color);
    paint.putArray("fill-extrusion-height").add("get").add("render_height");
    paint.putArray("fill-extrusion-base").add("get").add("render_min_height");
    paint.put("fill-extrusion-opacity", 0.8);
    layers.insert(insertAt, layer);
  }

  /** Templates no formato do MapLibre: {@code {z}}, {@code {fontstack}} etc. ficam literais. */
  public record Urls(String vectorTiles, String glyphs, String sprite) {}
}
