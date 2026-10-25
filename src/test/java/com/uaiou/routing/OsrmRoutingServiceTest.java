package com.uaiou.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.uaiou.routing.RoutingService.Matrix;
import com.uaiou.routing.RoutingService.Point;
import com.uaiou.routing.RoutingService.Route;
import com.uaiou.routing.config.RoutingProperties;
import com.uaiou.routing.service.OsrmRoutingService;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OsrmRoutingServiceTest {

  private static final String BASE = "http://osrm:5000";

  private static final List<Point> TRES_PONTOS =
      List.of(ponto("-22.25", "-45.70"), ponto("-22.26", "-45.71"), ponto("-22.27", "-45.72"));

  /** Duas pernas, cada uma com partida/curva/chegada — forma da resposta real com steps=true. */
  private static final String ROTA =
      """
      {"code":"Ok","routes":[{"distance":2345.6,"duration":301.4,
       "geometry":{"type":"LineString","coordinates":[
         [-45.70,-22.25],[-45.705,-22.255],[-45.71,-22.26],[-45.715,-22.265],[-45.72,-22.27]]},
       "legs":[
         {"steps":[
           {"distance":500,"duration":60,"name":"Rua A",
            "maneuver":{"type":"depart","location":[-45.70,-22.25]}},
           {"distance":600,"duration":70,"name":"Rua B",
            "maneuver":{"type":"turn","modifier":"right","location":[-45.705,-22.255]}},
           {"distance":0,"duration":0,"name":"Rua B",
            "maneuver":{"type":"arrive","location":[-45.71,-22.26]}}]},
         {"steps":[
           {"distance":700,"duration":90,"name":"Rua B",
            "maneuver":{"type":"depart","location":[-45.71,-22.26]}},
           {"distance":545,"duration":81,"name":"",
            "maneuver":{"type":"roundabout","modifier":"right","exit":2,
                        "location":[-45.715,-22.265]}},
           {"distance":0,"duration":0,"name":"",
            "maneuver":{"type":"arrive","location":[-45.72,-22.27]}}]}]}]}
      """;

  @Test
  void convertsGeometryStepsAndStopIndices() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(
            requestTo(
                BASE
                    + "/route/v1/driving/-45.70,-22.25;-45.71,-22.26;-45.72,-22.27"
                    + "?overview=full&geometries=geojson&steps=true"))
        .andRespond(withSuccess(ROTA, MediaType.APPLICATION_JSON));

    Route rota = servico(builder).route(TRES_PONTOS).orElseThrow();

    assertThat(rota.distanceKm()).isEqualByComparingTo("2.3");
    assertThat(rota.duration()).isEqualTo(Duration.ofSeconds(301));
    assertThat(rota.geometry()).hasSize(5);
    assertThat(rota.geometry().getFirst().lat()).isEqualByComparingTo("-22.25");
    assertThat(rota.waypointIndices()).containsExactly(2);
    assertThat(rota.steps())
        .extracting(RoutingService.Step::instruction)
        .containsExactly(
            "Siga pela Rua A",
            "Vire à direita na Rua B",
            "Você chegou ao destino",
            "Siga pela Rua B",
            "Na rotatória, pegue a 2ª saída",
            "Você chegou ao destino");
    assertThat(rota.steps())
        .extracting(RoutingService.Step::pointIndex)
        .containsExactly(0, 1, 2, 2, 3, 4);
    server.verify();
  }

  @Test
  void readsTheMatrixAndMarksUnreachablePairs() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(
            requestTo(
                BASE
                    + "/table/v1/driving/-45.70,-22.25;-45.71,-22.26;-45.72,-22.27"
                    + "?annotations=duration,distance"))
        .andRespond(
            withSuccess(
                """
                {"code":"Ok",
                 "durations":[[0,60.4,120],[61,0,null],[118,59,0]],
                 "distances":[[0,500,1000],[510,0,null],[990,480,0]]}
                """,
                MediaType.APPLICATION_JSON));

    Matrix matriz = servico(builder).matrix(TRES_PONTOS).orElseThrow();

    assertThat(matriz.size()).isEqualTo(3);
    assertThat(matriz.durationsSeconds()[0][1]).isEqualTo(60);
    assertThat(matriz.durationsSeconds()[1][2]).isEqualTo(Matrix.UNREACHABLE);
    assertThat(matriz.distancesMeters()[2][0]).isEqualTo(990);
  }

  @Test
  void aProviderFailureIsNoRouteNotAnError() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withServerError());

    Optional<Route> rota = servico(builder).route(TRES_PONTOS);

    assertThat(rota).isEmpty();
  }

  private OsrmRoutingService servico(RestClient.Builder builder) {
    return new OsrmRoutingService(
        builder.build(),
        new RoutingProperties(
            "osrm",
            "",
            BASE,
            "motorcycle",
            "pt",
            Duration.ofSeconds(3),
            Duration.ofDays(7),
            Duration.ofMinutes(5),
            3,
            "© OpenStreetMap contributors"));
  }

  private static Point ponto(String lat, String lng) {
    return new Point(new BigDecimal(lat), new BigDecimal(lng));
  }
}
