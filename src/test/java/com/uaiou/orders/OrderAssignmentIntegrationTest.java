package com.uaiou.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.blocks.dto.BlockCourierRequest;
import com.uaiou.notifications.dto.NotificationListResponse;
import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.DestinationResponse;
import com.uaiou.orders.dto.OrderListResponse;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.id.UuidV7;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-13.1 a RF-13.8 — critérios de aceite de T-13. */
class OrderAssignmentIntegrationTest extends AbstractAuthIntegrationTest {

  private static final String PERTO_LAT = "-19.918200";
  private static final String PERTO_LONG = "-43.938600";

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Critério 1: aceite de publicado retorna 201, atribui e move para aceito. */
  @Test
  void acceptingAPublishedOrderAssignsItAndMovesItToAccepted() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);

    ResponseEntity<AssignmentResponse> response = aceitar(courier, pedidoId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().courierId()).isEqualTo(courier.id());
    assertThat(response.getBody().finalFee()).isEqualTo(Money.of("6.00"));
    assertThat(response.getBody().assignedAt()).isNotNull();
    assertThat(statusNoBanco(pedidoId)).isEqualTo("aceito");
    assertThat(entregadorNoBanco(pedidoId)).isEqualTo(courier.id());
  }

  /** Critério 8: exatamente um código de entrega por pedido, e nunca em claro no banco. */
  @Test
  void acceptingCreatesExactlyOneDeliveryCodeNeverStoredInPlaintext() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchantComCredito());

    aceitar(courier, pedidoId);

    Integer total =
        jdbcTemplate.queryForObject(
            "select count(*) from otp where pedido_id = ?", Integer.class, pedidoId);
    assertThat(total).isEqualTo(1);

    var linha =
        jdbcTemplate.queryForMap(
            "select codigo_hash, codigo_cifrado, status from otp where pedido_id = ?", pedidoId);
    assertThat((String) linha.get("codigo_hash")).startsWith("sha256:");
    assertThat((String) linha.get("status")).isEqualTo("gerado");
    // Cifrado e hash precisam ser representações distintas — se fossem iguais, uma delas não é o
    // que diz ser.
    assertThat(linha.get("codigo_cifrado")).isNotEqualTo(linha.get("codigo_hash"));
    // 6 dígitos em claro nunca aparecem em nenhuma das colunas.
    assertThat((String) linha.get("codigo_cifrado")).doesNotMatch(".*\\b\\d{6}\\b.*");
  }

  /** Critério 2: N requisições simultâneas → exatamente uma 201, um único entregador_id. */
  @Test
  void concurrentAcceptsProduceExactlyOneWinner() throws Exception {
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    List<RegisteredTestUser> couriers = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      couriers.add(disponivel());
    }

    ExecutorService pool = Executors.newFixedThreadPool(couriers.size());
    CountDownLatch goGate = new CountDownLatch(1);
    List<Future<ResponseEntity<AssignmentResponse>>> futures = new ArrayList<>();
    for (RegisteredTestUser courier : couriers) {
      String token = login(courier).accessToken();
      futures.add(
          pool.submit(
              () -> {
                goGate.await();
                return restTemplate.exchange(
                    baseUrl("/orders/" + pedidoId + "/assignment"),
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders(token)),
                    AssignmentResponse.class);
              }));
    }
    goGate.countDown();

    int criados = 0;
    int conflitos = 0;
    for (Future<ResponseEntity<AssignmentResponse>> future : futures) {
      HttpStatus status = (HttpStatus) future.get(30, TimeUnit.SECONDS).getStatusCode();
      if (status == HttpStatus.CREATED) {
        criados++;
      } else if (status == HttpStatus.CONFLICT) {
        conflitos++;
      }
    }
    pool.shutdown();

    assertThat(criados).isEqualTo(1);
    assertThat(conflitos).isEqualTo(couriers.size() - 1);
    assertThat(entregadorNoBanco(pedidoId)).isNotNull();
    // E um único código, mesmo com 4 tentativas simultâneas.
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from otp where pedido_id = ?", Integer.class, pedidoId))
        .isEqualTo(1);
  }

  /** Critério 3: pedido já aceito → 409; inexistente → 404. */
  @Test
  void acceptingAnAlreadyAssignedOrderIsAConflictAndAnUnknownOrderIsNotFound() {
    RegisteredTestUser primeiro = disponivel();
    RegisteredTestUser segundo = disponivel();
    UUID pedidoId = publicar(merchantComCredito());
    aceitar(primeiro, pedidoId);

    ResponseEntity<ErrorResponse> conflito = aceitarComErro(segundo, pedidoId);
    assertThat(conflito.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflito.getBody().error().code()).isEqualTo("ORDER_ALREADY_ASSIGNED");

    ResponseEntity<ErrorResponse> inexistente = aceitarComErro(segundo, UUID.randomUUID());
    assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** Critério 7: retry devolve a MESMA atribuição, sem segunda escrita. */
  @Test
  void aRetryFromTheSameCourierReturnsTheSameAssignment() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchantComCredito());

    AssignmentResponse primeira = aceitar(courier, pedidoId).getBody();
    ResponseEntity<AssignmentResponse> retry = aceitar(courier, pedidoId);

    assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(retry.getBody().assignedAt()).isEqualTo(primeira.assignedAt());
    assertThat(retry.getBody().courierId()).isEqualTo(primeira.courierId());
    // Sem segunda escrita: continua havendo um só código.
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from otp where pedido_id = ?", Integer.class, pedidoId))
        .isEqualTo(1);
  }

  /** Critério 4: bloqueado entre a listagem e o aceite → 403. */
  @Test
  void aCourierBlockedBetweenListingAndAcceptGets403() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    // Viu na vitrine antes do bloqueio.
    assertThat(vitrine(courier).data()).anyMatch(o -> o.id().equals(pedidoId));

    restTemplate.exchange(
        baseUrl("/me/blocked-couriers"),
        HttpMethod.POST,
        authed(
            login(merchant).accessToken(), new BlockCourierRequest(courier.id(), "No intervalo")),
        Object.class);

    ResponseEntity<ErrorResponse> response = aceitarComErro(courier, pedidoId);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("BLOCKED_BY_MERCHANT");
    assertThat(statusNoBanco(pedidoId)).isEqualTo("publicado");
  }

  /** Critério 5: ficou indisponível no intervalo → 403. */
  @Test
  void aCourierWhoWentUnavailableInTheMeantimeGets403() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchantComCredito());
    setAvailability(login(courier).accessToken(), false);

    ResponseEntity<ErrorResponse> response = aceitarComErro(courier, pedidoId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("COURIER_NOT_AVAILABLE");
    assertThat(statusNoBanco(pedidoId)).isEqualTo("publicado");
  }

  /** Critério 6: contraofertas pendentes ficam invalidadas e seus autores são notificados. */
  @Test
  void pendingCounteroffersAreInvalidatedAndTheirAuthorsNotified() {
    RegisteredTestUser vencedor = disponivel();
    RegisteredTestUser proponente = disponivel();
    UUID pedidoId = publicar(merchantComCredito());
    // A criação de contraoferta é de T-14; a linha pendente é montada direto no banco.
    UUID contraofertaId = criarContraofertaPendente(pedidoId, proponente.id());

    aceitar(vencedor, pedidoId);

    assertThat(
            jdbcTemplate.queryForObject(
                "select status from contraoferta where id = ?", String.class, contraofertaId))
        .isEqualTo("invalidada");

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(temNotificacao(proponente, "counteroffer.decided", pedidoId)).isTrue());
  }

  @Test
  void theMerchantIsNotifiedOfTheAssignment() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);

    aceitar(courier, pedidoId);

    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> assertThat(temNotificacao(merchant, "order.assigned", pedidoId)).isTrue());
  }

  /** Critério 9: o pedido some da vitrine dos DEMAIS entregadores. */
  @Test
  void anAssignedOrderDisappearsFromEveryoneElsesFeed() {
    RegisteredTestUser vencedor = disponivel();
    RegisteredTestUser outro = disponivel();
    UUID pedidoId = publicar(merchantComCredito());
    assertThat(vitrine(outro).data()).anyMatch(o -> o.id().equals(pedidoId));

    aceitar(vencedor, pedidoId);

    assertThat(vitrine(outro).data()).noneMatch(o -> o.id().equals(pedidoId));
  }

  /** RF-13.8: a lista de entregas aceitas traz a âncora do tempo decorrido. */
  @Test
  void theCouriersAcceptedListCarriesTheAcceptedAtAnchor() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchantComCredito());
    aceitar(courier, pedidoId);

    OrderListResponse aceitos =
        restTemplate
            .exchange(
                baseUrl("/orders?status=accepted"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(courier).accessToken())),
                OrderListResponse.class)
            .getBody();

    var item =
        aceitos.data().stream().filter(o -> o.id().equals(pedidoId)).findFirst().orElseThrow();
    assertThat(item.acceptedAt()).isNotNull();
    assertThat(item.status()).isEqualTo(OrderStatus.ACCEPTED);
  }

  @Test
  void theAssignedCourierSeesTheFullDestinationInTheOrderDetail() {
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchantComCredito());
    aceitar(courier, pedidoId);

    OrderResponse detalhe =
        restTemplate
            .exchange(
                baseUrl("/orders/" + pedidoId),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(courier).accessToken())),
                OrderResponse.class)
            .getBody();

    DestinationResponse destino = detalhe.destination();
    assertThat(destino.street()).isNotBlank();
    assertThat(destino.number()).isNotBlank();
  }

  @Test
  void aMerchantCannotAcceptOrders() {
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/assignment"),
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(login(merchant).accessToken())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("COURIER_ONLY");
  }

  private ResponseEntity<AssignmentResponse> aceitar(RegisteredTestUser courier, UUID pedidoId) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/assignment"),
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(login(courier).accessToken())),
        AssignmentResponse.class);
  }

  private ResponseEntity<ErrorResponse> aceitarComErro(RegisteredTestUser courier, UUID pedidoId) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/assignment"),
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(login(courier).accessToken())),
        ErrorResponse.class);
  }

  private boolean temNotificacao(RegisteredTestUser user, String tipo, UUID pedidoId) {
    NotificationListResponse inbox =
        restTemplate
            .exchange(
                baseUrl("/me/notifications?type=" + tipo + "&perPage=100"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(user).accessToken())),
                NotificationListResponse.class)
            .getBody();
    return inbox.data().stream()
        .anyMatch(n -> pedidoId.toString().equals(n.payload().get("orderId")));
  }

  private UUID criarContraofertaPendente(UUID pedidoId, UUID entregadorId) {
    UUID id = UuidV7.next();
    jdbcTemplate.update(
        "insert into contraoferta (id, pedido_id, entregador_id, valor_proposto, status)"
            + " values (?, ?, ?, ?, 'pendente')",
        id,
        pedidoId,
        entregadorId,
        new BigDecimal("9.00"));
    return id;
  }

  private String statusNoBanco(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select status from pedido where id = ?", String.class, pedidoId);
  }

  private UUID entregadorNoBanco(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select entregador_id from pedido where id = ?", UUID.class, pedidoId);
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
        authed(
            token,
            new UpdateLocationRequest(
                new BigDecimal(PERTO_LAT), new BigDecimal(PERTO_LONG), new BigDecimal("10.0"))),
        Void.class);
    setAvailability(token, true);
    return courier;
  }

  private void setAvailability(String token, boolean available) {
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(available)),
        Object.class);
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
                new BigDecimal("-19.925100"),
                new BigDecimal("-43.941700")),
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

  private OrderListResponse vitrine(RegisteredTestUser courier) {
    return restTemplate
        .exchange(
            baseUrl("/orders?status=published&perPage=100"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            OrderListResponse.class)
        .getBody();
  }
}
