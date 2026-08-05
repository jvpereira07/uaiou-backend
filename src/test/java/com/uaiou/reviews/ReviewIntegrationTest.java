package com.uaiou.reviews;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DeliveryCodeResponse;
import com.uaiou.orders.dto.DeliveryCompletionRequest;
import com.uaiou.orders.dto.DeliveryCompletionResponse;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.reviews.dto.CreateReviewRequest;
import com.uaiou.reviews.dto.ReceivedReviewsResponse;
import com.uaiou.reviews.dto.ReviewResponse;
import com.uaiou.reviews.service.ReviewDefaultJob;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-19.1 a RF-19.9 — critérios de aceite de T-19. */
class ReviewIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ReviewDefaultJob reviewDefaultJob;

  /** Critério 1: avaliar pedido não finalizado → 422. */
  @Test
  void reviewingANonFinalizedOrderFails() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchant);
    aceitar(courier, pedidoId);

    ResponseEntity<ErrorResponse> resposta = avaliarComErro(merchant, pedidoId, 5, "ok");

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("ORDER_NOT_FINALIZED");
  }

  /** Critério 1: avaliar pedido de terceiro → 403. */
  @Test
  void reviewingSomeoneElsesOrderIsForbidden() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    RegisteredTestUser estranho = merchantComCredito();

    ResponseEntity<ErrorResponse> resposta = avaliarComErro(estranho, pedidoId, 5, "ok");

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resposta.getBody().error().code()).isEqualTo("NOT_A_PARTY");
  }

  /** Critério 2: duas avaliações do mesmo autor no mesmo pedido → 409. */
  @Test
  void aSecondReviewFromTheSameAuthorIsAConflict() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    avaliar(merchant, pedidoId, 5, "Ótimo");

    ResponseEntity<ErrorResponse> resposta = avaliarComErro(merchant, pedidoId, 4, "De novo");

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resposta.getBody().error().code()).isEqualTo("ALREADY_REVIEWED");
  }

  /** Critério 3: nota fora de 1..5 → 400. */
  @Test
  void ratingOutOfRangeIsRejected() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);

    ResponseEntity<ErrorResponse> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/reviews"),
            HttpMethod.POST,
            authed(login(merchant).accessToken(), new CreateReviewRequest(6, "Nota inválida")),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  /** Critério 4: job cria a automática (ativa=false) só pro lado omisso, após o prazo. */
  @Test
  void theJobCreatesTheDefaultOnlyForTheMissingSideAfterTheWindow() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    avaliar(merchant, pedidoId, 5, "Só o estabelecimento avaliou");
    vencerJanela(pedidoId);

    reviewDefaultJob.fecharCiclosVencidos();

    Integer avaliacoesDoEntregador =
        jdbcTemplate.queryForObject(
            "select count(*) from avaliacao where pedido_id = ? and autor_id = ?",
            Integer.class,
            pedidoId,
            courier.id());
    assertThat(avaliacoesDoEntregador).isEqualTo(1);
    Boolean ativaAutomatica =
        jdbcTemplate.queryForObject(
            "select ativa from avaliacao where pedido_id = ? and autor_id = ?",
            Boolean.class,
            pedidoId,
            courier.id());
    assertThat(ativaAutomatica).isFalse();

    // O lado que já avaliou de verdade não foi tocado.
    Boolean ativaDoEstabelecimento =
        jdbcTemplate.queryForObject(
            "select ativa from avaliacao where pedido_id = ? and autor_id = ?",
            Boolean.class,
            pedidoId,
            merchant.id());
    assertThat(ativaDoEstabelecimento).isTrue();
  }

  /** Critério 5: antes de ambas existirem e dentro do prazo, cada parte não vê a da outra. */
  @Test
  void beforeBothExistAndWithinTheWindowNeitherSideSeesTheOthers() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    avaliar(merchant, pedidoId, 5, "Só eu avaliei até agora");

    ReceivedReviewsResponse recebidasPeloEntregador = recebidas(courier);

    assertThat(recebidasPeloEntregador.data()).isEmpty();
  }

  /** Critério 6: summary.activeRate reflete a proporção de avaliações reais. */
  @Test
  void summaryActiveRateReflectsTheProportionOfRealReviews() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    avaliar(merchant, pedidoId, 5, "Real");
    vencerJanela(pedidoId);
    reviewDefaultJob.fecharCiclosVencidos();

    // Agora ambas existem: a do estabelecimento (real, ativa) e a automática do entregador sobre o
    // estabelecimento — mas o que o ENTREGADOR recebeu é só a do estabelecimento, ativa.
    ReceivedReviewsResponse recebidasPeloEntregador = recebidas(courier);

    assertThat(recebidasPeloEntregador.data()).hasSize(1);
    assertThat(recebidasPeloEntregador.summary().activeRate()).isEqualByComparingTo("1.00");
  }

  /** Critério 7: criar avaliação dispara recálculo do score do alvo. */
  @Test
  void creatingAReviewTriggersTheTargetsScoreRecalculation() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);

    avaliar(merchant, pedidoId, 5, "Ótimo entregador");

    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              BigDecimal score =
                  jdbcTemplate.queryForObject(
                      "select score from entregador where usuario_id = ?",
                      BigDecimal.class,
                      courier.id());
              assertThat(score).isNotNull();
            });
  }

  /** Critério 8: avaliação não altera nenhum lançamento do livro-razão. */
  @Test
  void reviewingDoesNotChangeAnyLedgerEntry() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    BigDecimal valorAntes =
        jdbcTemplate.queryForObject(
            "select valor from lancamento_frete where pedido_id = ?", BigDecimal.class, pedidoId);
    String statusAntes =
        jdbcTemplate.queryForObject(
            "select status from lancamento_frete where pedido_id = ?", String.class, pedidoId);

    avaliar(merchant, pedidoId, 1, "Nota baixa, sem efeito financeiro");

    BigDecimal valorDepois =
        jdbcTemplate.queryForObject(
            "select valor from lancamento_frete where pedido_id = ?", BigDecimal.class, pedidoId);
    String statusDepois =
        jdbcTemplate.queryForObject(
            "select status from lancamento_frete where pedido_id = ?", String.class, pedidoId);
    assertThat(valorDepois).isEqualByComparingTo(valorAntes);
    assertThat(statusDepois).isEqualTo(statusAntes);
  }

  private void vencerJanela(UUID pedidoId) {
    jdbcTemplate.update(
        "update pedido set finalizado_em = ? where id = ?",
        java.sql.Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)),
        pedidoId);
  }

  private ResponseEntity<ReviewResponse> avaliar(
      RegisteredTestUser autor, UUID pedidoId, int nota, String comentario) {
    ResponseEntity<ReviewResponse> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/reviews"),
            HttpMethod.POST,
            authed(login(autor).accessToken(), new CreateReviewRequest(nota, comentario)),
            ReviewResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return resposta;
  }

  private ResponseEntity<ErrorResponse> avaliarComErro(
      RegisteredTestUser autor, UUID pedidoId, int nota, String comentario) {
    return restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/reviews"),
        HttpMethod.POST,
        authed(login(autor).accessToken(), new CreateReviewRequest(nota, comentario)),
        ErrorResponse.class);
  }

  private ReceivedReviewsResponse recebidas(RegisteredTestUser user) {
    return restTemplate
        .exchange(
            baseUrl("/me/reviews?direction=received"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(user).accessToken())),
            ReceivedReviewsResponse.class)
        .getBody();
  }

  private void aceitar(RegisteredTestUser courier, UUID pedidoId) {
    ResponseEntity<AssignmentResponse> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/assignment"),
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            AssignmentResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
    ResponseEntity<DeliveryCompletionResponse> resposta =
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
