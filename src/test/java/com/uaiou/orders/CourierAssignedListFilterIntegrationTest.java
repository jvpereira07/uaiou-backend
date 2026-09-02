package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DeliveryCodeResponse;
import com.uaiou.orders.dto.DeliveryCompletionRequest;
import com.uaiou.orders.dto.DeliveryCompletionResponse;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderListResponse;
import com.uaiou.orders.dto.OrderResponse;
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

/**
 * RF-11.6 — {@code GET /orders?status=} tem de RECORTAR, não só ser aceito.
 *
 * <p>O controller validava o valor e descartava: o serviço devolvia sempre os três status do
 * entregador. Quem pagava era o app — a tela principal trata o primeiro item de {@code accepted}
 * como a entrega em curso (RF-A08.9), então uma entrega recém-finalizada voltava na lista e o
 * entregador era devolvido à tela dela ao tentar ir para a principal.
 */
class CourierAssignedListFilterIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");

  @Autowired private OrderTestFixtures orders;

  @Test
  void theAcceptedListDoesNotCarryAnAlreadyFinalizedDelivery() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();

    UUID finalizado = finalizarPedido(merchant, courier);
    UUID emCurso = publicar(merchant);
    aceitar(courier, emCurso);

    OrderListResponse aceitos = listar(courier, "accepted");

    assertThat(aceitos.data()).noneMatch(o -> o.id().equals(finalizado));
    assertThat(aceitos.data()).anyMatch(o -> o.id().equals(emCurso));
    assertThat(aceitos.data()).allMatch(o -> o.status() == OrderStatus.ACCEPTED);
  }

  @Test
  void theFinalizedListDoesNotCarryTheDeliveryStillInProgress() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();

    UUID finalizado = finalizarPedido(merchant, courier);
    UUID emCurso = publicar(merchant);
    aceitar(courier, emCurso);

    OrderListResponse concluidos = listar(courier, "finalized");

    assertThat(concluidos.data()).anyMatch(o -> o.id().equals(finalizado));
    assertThat(concluidos.data()).noneMatch(o -> o.id().equals(emCurso));
  }

  private OrderListResponse listar(RegisteredTestUser courier, String status) {
    return restTemplate
        .exchange(
            baseUrl("/orders?status=" + status),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            OrderListResponse.class)
        .getBody();
  }

  private void aceitar(RegisteredTestUser courier, UUID pedidoId) {
    restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/assignment"),
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(login(courier).accessToken())),
        AssignmentResponse.class);
  }

  private UUID finalizarPedido(RegisteredTestUser merchant, RegisteredTestUser courier) {
    UUID pedidoId = publicar(merchant);
    aceitar(courier, pedidoId);

    String codigo =
        restTemplate
            .exchange(
                baseUrl("/orders/" + pedidoId + "/delivery/code"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                DeliveryCodeResponse.class)
            .getBody()
            .code();

    var resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/delivery/completion"),
            HttpMethod.POST,
            authed(
                login(courier).accessToken(),
                new DeliveryCompletionRequest(
                    DeliveryCompletionRequest.Mode.CODE, codigo, null, DEST_LAT, DEST_LNG)),
            DeliveryCompletionResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return pedidoId;
  }

  private RegisteredTestUser merchantComCredito() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    return merchant;
  }

  private RegisteredTestUser disponivel() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    restTemplate.exchange(
        baseUrl("/me/location"),
        HttpMethod.PUT,
        authed(token, new UpdateLocationRequest(DEST_LAT, DEST_LNG, new BigDecimal("10.0"))),
        Void.class);
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(true)),
        Object.class);
    return courier;
  }

  private UUID publicar(RegisteredTestUser merchant) {
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
                DEST_LAT,
                DEST_LNG),
            new CreateOrderRequest.ReceiverRequest("Marina", "31998877665"));
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
