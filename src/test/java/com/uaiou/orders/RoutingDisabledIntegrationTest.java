package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.orders.dto.OrderRouteResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Critério de aceite 6 de T-25: <strong>sem chave configurada</strong> o recurso desliga limpo.
 * Roda no contexto padrão da suíte, que é exatamente esse cenário ({@code ROUTING_API_KEY} não
 * existe no perfil de teste) — se o arranque dependesse da chave, nenhum outro teste de integração
 * subiria.
 */
class RoutingDisabledIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void withoutAnApiKeyTheRouteComesAbsentAndNothingElseBreaks() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    // Com coordenada: sem ela o motivo seria "estabelecimento sem ponto", e o que está em teste
    // aqui
    // é o provedor desligado.
    jdbcTemplate.update(
        "update estabelecimento set lat = ?, \"long\" = ? where usuario_id = ?",
        new BigDecimal("-19.930000"),
        new BigDecimal("-43.935000"),
        merchant.id());
    UUID pedidoId = publicarPedidoDe(merchant);
    RegisteredTestUser courier = disponivelPertoDoDestino();

    ResponseEntity<OrderRouteResponse> response =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/route"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            OrderRouteResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().route().available()).isFalse();
    assertThat(response.getBody().route().unavailableReason()).isEqualTo("ROUTING_UNAVAILABLE");
    // Sem rota nenhuma não há o que atribuir (RNF-25.2).
    assertThat(response.getBody().attribution()).isNull();
  }

  private RegisteredTestUser disponivelPertoDoDestino() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    restTemplate.exchange(
        baseUrl("/me/location"),
        HttpMethod.PUT,
        authed(
            token,
            new UpdateLocationRequest(
                new BigDecimal("-19.918200"),
                new BigDecimal("-43.938600"),
                new BigDecimal("10.0"))),
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
                new BigDecimal("-19.925100"),
                new BigDecimal("-43.941700")),
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
}
