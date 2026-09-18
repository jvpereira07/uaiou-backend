package com.uaiou.maptiles;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.maptiles.service.MapStyleRewriter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Estrutura recortada dos estilos reais do provedor ({@code dark-matter-yellow-roads}). */
class MapStyleRewriterTest {

  private static final String DARK_STYLE =
      """
      {"version":8,
       "sprite":"https://maps.geoapify.com/v1/styles/dark/sprite?apiKey=SECRET",
       "glyphs":"https://maps.geoapify.com/v1/styles/dark/fonts/{fontstack}/{range}.pbf?apiKey=SECRET",
       "sources":{"default":{"type":"vector","url":"https://maps.geoapify.com/v1/styles/dark/data.json?apiKey=SECRET"}},
       "layers":[
         {"id":"background","type":"background"},
         {"id":"building","type":"fill","source":"default","source-layer":"building"},
         {"id":"road-label","type":"symbol","source":"default","source-layer":"transportation_name"}
       ]}
      """;

  private static final MapStyleRewriter.Urls URLS =
      new MapStyleRewriter.Urls(
          "https://api/map-tiles/vector/{z}/{x}/{y}.pbf?t=T",
          "https://api/map-tiles/fonts/dark/{fontstack}/{range}.pbf?t=T",
          "https://api/map-tiles/sprites/dark/sprite?t=T");

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void neverLeaksTheProviderKey() {
    String rewritten = mapper.writeValueAsString(rewrite(null));

    assertThat(rewritten).doesNotContain("SECRET").doesNotContain("maps.geoapify.com");
  }

  @Test
  void pointsEveryResourceAtTheProxyWithLiteralTemplates() {
    JsonNode style = rewrite(null);

    JsonNode source = style.path("sources").path("default");
    assertThat(source.path("tiles").get(0).asString()).isEqualTo(URLS.vectorTiles());
    assertThat(source.path("maxzoom").asInt()).isEqualTo(14);
    assertThat(source.has("url")).isFalse();
    assertThat(style.path("glyphs").asString()).isEqualTo(URLS.glyphs());
    assertThat(style.path("sprite").asString()).isEqualTo(URLS.sprite());
  }

  @Test
  void addsExtrudedBuildingsRightAboveTheFlatOnes() {
    JsonNode layers = rewrite("#2b2b30").path("layers");

    assertThat(layers.size()).isEqualTo(4);
    JsonNode extruded = layers.get(2);
    assertThat(extruded.path("type").asString()).isEqualTo("fill-extrusion");
    assertThat(extruded.path("source").asString()).isEqualTo("default");
    assertThat(layers.get(3).path("id").asString()).isEqualTo("road-label");
  }

  @Test
  void leavesAnExistingExtrusionAlone() {
    ObjectNode style = (ObjectNode) mapper.readTree(DARK_STYLE);
    ((tools.jackson.databind.node.ArrayNode) style.path("layers"))
        .addObject()
        .put("id", "own-3d")
        .put("type", "fill-extrusion");

    JsonNode layers = MapStyleRewriter.rewrite(style, URLS, "attr", "#000").path("layers");

    assertThat(layers.size()).isEqualTo(4);
  }

  private ObjectNode rewrite(String buildingsColor) {
    ObjectNode style = (ObjectNode) mapper.readTree(DARK_STYLE);
    return MapStyleRewriter.rewrite(style, URLS, "attr", buildingsColor);
  }
}
