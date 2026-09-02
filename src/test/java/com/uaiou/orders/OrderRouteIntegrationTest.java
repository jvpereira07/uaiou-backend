package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderListResponse;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.orders.dto.OrderRouteResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.routing.RoutingService;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Critérios de aceite 1 a 5 e 8 de T-25.
 *
 * <p>O provedor é substituído por um duplo que <strong>conta chamadas</strong>: é a única forma de
 * afirmar sobre cache (critério 4) e sobre "a listagem não chama o provedor" (critério 8) — o que
 * está em teste é o consumo, não a aritmética de um provedor real.
 */
@Import(OrderRouteIntegrationTest.RoutingStubConfiguration.class)
class OrderRouteIntegrationTest extends AbstractAuthIntegrationTest {

  // Destino do pedido: Jardim Independência, BH.
  private static final String DEST_LAT = "-19.925100";
  private static final String DEST_LONG = "-43.941700";
  // Estabelecimento e entregador a poucos km — dentro do raio padrão.
  private static final String LOJA_LAT = "-19.930000";
  private static final String LOJA_LONG = "-43.935000";
  private static final String ENTREGADOR_LAT = "-19.918200";
  private static final String ENTREGADOR_LONG = "-43.938600";

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void resetarProvedor() {
    RoutingStubConfiguration.CHAMADAS.set(0);
    RoutingStubConfiguration.DISPONIVEL.set(true);
  }

  /**
   * Critérios 1 e 3: uma rota só, passando pela loja, com distância, duração, traçado e instruções
   * — e a linha reta continua sendo outro número, com outro nome.
   */
  @Test
  void theRouteComesAsOneTripThroughTheMerchant() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicarPedidoDe(merchant);
    RegisteredTestUser courier = disponivelEm(ENTREGADOR_LAT, ENTREGADOR_LONG);

    OrderRouteResponse resposta = rotaDe(courier, pedidoId).getBody();
    OrderRouteResponse.Route rota = resposta.route();

    assertThat(rota.available()).isTrue();
    assertThat(rota.includesPickup()).isTrue();
    assertThat(rota.roadDistanceKm()).isNotNull();
    assertThat(rota.durationMinutes()).isNotNull();
    assertThat(rota.geometry()).isNotEmpty();
    assertThat(rota.steps()).isNotEmpty();
    // Critério 3: a linha reta continua existindo, com nome próprio, e não é a distância por via.
    assertThat(resposta.straightLineDistanceKm()).isNotNull();
    assertThat(resposta.straightLineDistanceKm()).isNotEqualByComparingTo(rota.roadDistanceKm());
    assertThat(resposta.attribution()).isNotBlank();
  }

  /**
   * Critério 2, no desenho de rota única: sem coordenada da loja o trajeto existe do mesmo jeito,
   * direto ao destino, e a resposta avisa que ele não passa pela retirada.
   */
  @Test
  void anOrderFromAMerchantWithoutCoordinatesStillGetsADirectRoute() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    UUID pedidoId = publicarPedidoDe(merchant);
    RegisteredTestUser courier = disponivelEm(ENTREGADOR_LAT, ENTREGADOR_LONG);

    ResponseEntity<OrderRouteResponse> response = rotaDe(courier, pedidoId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().route().available()).isTrue();
    assertThat(response.getBody().route().includesPickup()).isFalse();
    assertThat(RoutingStubConfiguration.ULTIMA_CHAMADA.get()).hasSize(2);
  }

  /**
   * Critério 4 no desenho novo: o cache passou a ser por POSIÇÃO de quem pergunta, não por pedido.
   * Dois entregadores no mesmo ponto arredondado ainda dividem a chamada — o que se perdeu, e está
   * registrado em T-25, é o compartilhamento entre entregadores distantes entre si.
   */
  @Test
  void twoCouriersAtTheSameSpotShareOneProviderCall() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicarPedidoDe(merchant);
    RegisteredTestUser primeiro = disponivelEm(ENTREGADOR_LAT, ENTREGADOR_LONG);
    RegisteredTestUser segundo = disponivelEm(ENTREGADOR_LAT, ENTREGADOR_LONG);

    rotaDe(primeiro, pedidoId);
    int aposOPrimeiro = RoutingStubConfiguration.CHAMADAS.get();
    rotaDe(segundo, pedidoId);

    // Uma rota, uma chamada; o segundo, na mesma posição arredondada, não paga nada.
    assertThat(aposOPrimeiro).isEqualTo(1);
    assertThat(RoutingStubConfiguration.CHAMADAS).hasValue(1);
  }

  /** Critério 5: provedor fora do ar não impede ver nem aceitar — a rota só vem ausente. */
  @Test
  void aProviderOutageLeavesTheOrderListableAndAcceptable() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicarPedidoDe(merchant);
    RegisteredTestUser courier = disponivelEm(ENTREGADOR_LAT, ENTREGADOR_LONG);
    RoutingStubConfiguration.DISPONIVEL.set(false);

    ResponseEntity<OrderRouteResponse> rota = rotaDe(courier, pedidoId);

    assertThat(rota.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(rota.getBody().route().available()).isFalse();
    assertThat(rota.getBody().route().unavailableReason()).isEqualTo("ROUTING_UNAVAILABLE");
    assertThat(vitrineDe(courier).data()).anyMatch(o -> o.id().equals(pedidoId));

    String token = login(courier).accessToken();
    ResponseEntity<Object> aceite =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/assignment"),
            HttpMethod.POST,
            new HttpEntity<>(null, authHeaders(token)),
            Object.class);
    assertThat(aceite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  /** Critério 8: a rota mais quente do serviço não passa a depender de um terceiro. */
  @Test
  void listingOrdersNeverCallsTheProvider() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicarPedidoDe(merchant);
    RegisteredTestUser courier = disponivelEm(ENTREGADOR_LAT, ENTREGADOR_LONG);

    assertThat(vitrineDe(courier).data()).anyMatch(o -> o.id().equals(pedidoId));
    assertThat(RoutingStubConfiguration.CHAMADAS).hasValue(0);
  }

  /** A rota é do entregador que pode agir sobre o pedido — não de quem não o enxerga. */
  @Test
  void aCourierWhoCannotSeeTheOrderCannotSeeItsRoute() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicarPedidoDe(merchant);
    // Contagem/BH, ~30 km: fora do raio de elegibilidade.
    RegisteredTestUser longe = disponivelEm("-19.931700", "-44.053800");

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/route"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(longe).accessToken())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("ORDER_NOT_VISIBLE");
  }

  private ResponseEntity<OrderRouteResponse> rotaDe(RegisteredTestUser courier, UUID pedidoId) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/route"),
        HttpMethod.GET,
        new HttpEntity<>(authHeaders(login(courier).accessToken())),
        OrderRouteResponse.class);
  }

  private OrderListResponse vitrineDe(RegisteredTestUser courier) {
    return restTemplate
        .exchange(
            baseUrl("/orders?status=published&perPage=100"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            OrderListResponse.class)
        .getBody();
  }

  /**
   * A coordenada do estabelecimento nasce nula (V21) e só é preenchida quando ele marca o ponto no
   * mapa — aqui o atalho é SQL, como no resto da suíte para estado de outra task.
   */
  private RegisteredTestUser merchantComCoordenada() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    jdbcTemplate.update(
        "update estabelecimento set lat = ?, \"long\" = ? where usuario_id = ?",
        new BigDecimal(LOJA_LAT),
        new BigDecimal(LOJA_LONG),
        merchant.id());
    return merchant;
  }

  private RegisteredTestUser disponivelEm(String lat, String lng) {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    restTemplate.exchange(
        baseUrl("/me/location"),
        HttpMethod.PUT,
        authed(
            token,
            new UpdateLocationRequest(
                new BigDecimal(lat), new BigDecimal(lng), new BigDecimal("10.0"))),
        Void.class);
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(true)),
        Object.class);
    return courier;
  }

  private UUID publicarPedidoDe(RegisteredTestUser merchant) {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Money.of("6.00"),
            null,
            new DestinationRequest(
                "Rua Beija-Flor",
                "45",
                null,
                "Jardim Independencia",
                "Belo Horizonte",
                new BigDecimal(DEST_LAT),
                new BigDecimal(DEST_LONG)),
            new CreateOrderRequest.ReceiverRequest("Marina Alves", "31998877665"));
    return restTemplate
        .exchange(
            baseUrl("/orders"),
            HttpMethod.POST,
            authed(login(merchant).accessToken(), request),
            OrderResponse.class)
        .getBody()
        .id();
  }

  /**
   * Duplo do provedor. Substitui o bean de {@code RoutingConfiguration} sem que nenhum chamador
   * saiba — que é exatamente o que RF-25.1 comprou ao isolar a escolha atrás da interface.
   */
  @TestConfiguration
  static class RoutingStubConfiguration {

    static final AtomicInteger CHAMADAS = new AtomicInteger();
    static final AtomicBoolean DISPONIVEL = new AtomicBoolean(true);

    /**
     * Os waypoints da última chamada — é o que prova que a loja entra como parada intermediária
     * quando tem coordenada, e some do trajeto quando não tem.
     */
    static final AtomicReference<List<RoutingService.Point>> ULTIMA_CHAMADA =
        new AtomicReference<>(List.of());

    @Bean
    @Primary
    RoutingService stubRoutingService() {
      return waypoints -> {
        CHAMADAS.incrementAndGet();
        ULTIMA_CHAMADA.set(List.copyOf(waypoints));
        if (!DISPONIVEL.get()) {
          return Optional.empty();
        }
        return Optional.of(
            new RoutingService.Route(
                new BigDecimal("3.7"),
                Duration.ofSeconds(600),
                List.copyOf(waypoints),
                List.of(new RoutingService.Step("Siga em frente", 400, 60, 0))));
      };
    }
  }
}
