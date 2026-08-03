package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-11.1 a RF-11.4 — critérios de aceite 1, 2, 3 e 4 de T-11. */
class OrderCreationIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private OrderTestFixtures orders;

  @Test
  void creatingWithEnoughCreditsDebitsAndPublishes() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    String token = login(merchant).accessToken();

    ResponseEntity<OrderResponse> response = create(token, destinoValido(), "6.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().status()).isEqualTo(OrderStatus.PUBLISHED);
    assertThat(response.getBody().creditsConsumed()).isEqualTo(1);
    assertThat(response.getBody().number()).isEqualTo("0001");
    assertThat(orders.saldoDe(merchant.id())).isEqualTo(4);
    // Critério 1 fala do estado do pedido, não do eco da resposta: confere o que ficou no banco.
    assertThat(orders.statusPersistidoDe(response.getBody().id())).isEqualTo("publicado");
  }

  @Test
  void creatingWithoutCreditsIsRejectedAndCreatesNoOrder() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 0);
    String token = login(merchant).accessToken();

    ResponseEntity<ErrorResponse> response = createExpectingError(token, destinoValido(), "6.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("INSUFFICIENT_CREDITS");
    assertThat(response.getBody().error().rule()).isEqualTo("RN-05.1");
    // Critério 2: a transação inteira reverteu — nem pedido, nem número consumido.
    assertThat(orders.contarPedidosDe(merchant.id())).isZero();
  }

  @Test
  void anUngeocodableAddressIsRejectedAndConsumesNoCredit() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    String token = login(merchant).accessToken();

    DestinationRequest semCoordenadas =
        new DestinationRequest(
            "Rua Beija-Flor", "45", null, "Jardim Independencia", "Belo Horizonte", null, null);
    ResponseEntity<ErrorResponse> response = createExpectingError(token, semCoordenadas, "6.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(response.getBody().error().code()).isEqualTo("UNGEOCODABLE_ADDRESS");
    // Critério 3: a geocodificação falha ANTES do crédito ser tocado (RF-11.1, ordem das
    // validações).
    assertThat(orders.saldoDe(merchant.id())).isEqualTo(5);
    assertThat(orders.contarPedidosDe(merchant.id())).isZero();
  }

  @Test
  void nullIslandCoordinatesAreRejected() {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    String token = login(merchant).accessToken();

    DestinationRequest nullIsland =
        new DestinationRequest(
            "Rua Beija-Flor",
            "45",
            null,
            "Jardim Independencia",
            "Belo Horizonte",
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    ResponseEntity<ErrorResponse> response = createExpectingError(token, nullIsland, "6.00");

    assertThat(response.getBody().error().code()).isEqualTo("UNGEOCODABLE_ADDRESS");
  }

  @Test
  void numbersAreSequentialPerMerchant() {
    RegisteredTestUser a = registerAndActivateMerchant();
    RegisteredTestUser b = registerAndActivateMerchant();
    orders.darCreditos(a.id(), 5);
    orders.darCreditos(b.id(), 5);
    String tokenA = login(a).accessToken();
    String tokenB = login(b).accessToken();

    assertThat(create(tokenA, destinoValido(), "6.00").getBody().number()).isEqualTo("0001");
    assertThat(create(tokenA, destinoValido(), "6.00").getBody().number()).isEqualTo("0002");
    // Numeração é POR estabelecimento: a loja B começa do 1 de novo.
    assertThat(create(tokenB, destinoValido(), "6.00").getBody().number()).isEqualTo("0001");
  }

  /** Critério de aceite 4: dois pedidos simultâneos do mesmo estabelecimento não colidem. */
  @Test
  void concurrentCreationsGetDistinctNumbers() throws Exception {
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 10);
    String token = login(merchant).accessToken();

    ExecutorService pool = Executors.newFixedThreadPool(4);
    CountDownLatch goGate = new CountDownLatch(1);
    List<Future<ResponseEntity<OrderResponse>>> futures = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      futures.add(
          pool.submit(
              () -> {
                goGate.await();
                return create(token, destinoValido(), "6.00");
              }));
    }
    goGate.countDown();

    List<String> numeros = new ArrayList<>();
    for (Future<ResponseEntity<OrderResponse>> future : futures) {
      ResponseEntity<OrderResponse> response = future.get(30, TimeUnit.SECONDS);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      numeros.add(response.getBody().number());
    }
    pool.shutdown();

    assertThat(numeros).doesNotHaveDuplicates().hasSize(4);
    assertThat(orders.saldoDe(merchant.id())).isEqualTo(6);
  }

  @Test
  void aPendingMerchantCannotPublish() {
    RegisteredTestUser merchant = registerMerchant();
    orders.darCreditos(merchant.id(), 5);
    String token = login(merchant).accessToken();

    ResponseEntity<ErrorResponse> response = createExpectingError(token, destinoValido(), "6.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("ACCOUNT_NOT_ACTIVE");
  }

  @Test
  void aCourierCannotPublish() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();

    ResponseEntity<ErrorResponse> response = createExpectingError(token, destinoValido(), "6.00");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("MERCHANT_ONLY");
  }

  static DestinationRequest destinoValido() {
    return new DestinationRequest(
        "Rua Beija-Flor",
        "45",
        null,
        "Jardim Independencia",
        "Belo Horizonte",
        new BigDecimal("-19.925100"),
        new BigDecimal("-43.941700"));
  }

  private ResponseEntity<OrderResponse> create(
      String token, DestinationRequest destino, String frete) {
    return restTemplate.exchange(
        baseUrl("/orders"),
        HttpMethod.POST,
        authed(token, pedido(destino, frete)),
        OrderResponse.class);
  }

  private ResponseEntity<ErrorResponse> createExpectingError(
      String token, DestinationRequest destino, String frete) {
    return restTemplate.exchange(
        baseUrl("/orders"),
        HttpMethod.POST,
        authed(token, pedido(destino, frete)),
        ErrorResponse.class);
  }

  private CreateOrderRequest pedido(DestinationRequest destino, String frete) {
    return new CreateOrderRequest(
        Money.of(frete),
        null,
        destino,
        new CreateOrderRequest.ReceiverRequest("Marina Alves", "31998877665"));
  }
}
