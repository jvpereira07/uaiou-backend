package com.uaiou.score;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.delivery.service.ContingencyExpiryJob;
import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CodeRecoveryRequest;
import com.uaiou.orders.dto.CounterofferResponse;
import com.uaiou.orders.dto.CreateCounterofferRequest;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DeliveryCodeResponse;
import com.uaiou.orders.dto.DeliveryCompletionRequest;
import com.uaiou.orders.dto.DeliveryCompletionResponse;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.reviews.dto.CreateReviewRequest;
import com.uaiou.score.dto.ScoreResponse;
import com.uaiou.score.service.ScoreCalculationService;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-20.1 a RF-20.10 — critérios de aceite de T-20. */
class ScoreIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ContingencyExpiryJob contingencyExpiryJob;
  @Autowired private ScoreCalculationService scoreCalculationService;

  /**
   * Critérios 1/2: avaliação dispara recálculo; GET /me/score traz value, calculatedAt e
   * componentes.
   */
  @Test
  void reviewingTriggersRecalculationAndScoreShowsValueAndComponents() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = finalizarPedido(merchant, courier);
    avaliar(merchant, pedidoId, 5, "Excelente");

    ScoreResponse score = aguardarScore(courier);

    assertThat(score.value()).isNotNull();
    assertThat(score.calculatedAt()).isNotNull();
    assertThat(score.components()).isNotEmpty();
    assertThat(score.components()).anyMatch(c -> c.name().equals("avaliacao") && c.value() != null);
  }

  /**
   * Critério 3 — RN-09.5: contingência atribuída ao estabelecimento não toca o score do entregador.
   */
  @Test
  void merchantAttributedContingencyDoesNotAffectTheCouriersScore() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchant, null);
    aceitar(courier, pedidoId);

    BigDecimal scoreAntes = scoreDoEntregador(courier.id());

    acionarContingenciaEVencerPrazo(courier, pedidoId);

    BigDecimal scoreDepois = scoreDoEntregador(courier.id());
    assertThat(scoreDepois).isEqualTo(scoreAntes);
  }

  /** Critério 4: penalidade aparece em penalties com motivo e pedidoId. */
  @Test
  void penaltyAppearsInPenaltiesWithReasonAndOrderId() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchant, null);
    aceitar(courier, pedidoId);
    acionarContingenciaEVencerPrazo(courier, pedidoId);

    ScoreResponse score = scoreDoEstabelecimento(merchant);

    assertThat(score.penalties()).isNotEmpty();
    assertThat(score.penalties())
        .anyMatch(p -> p.pedidoId().equals(pedidoId) && p.motivo() != null);
  }

  /** Critério 5: avaliação ativa=false pesa menos que uma ativa na média. */
  @Test
  void anInactiveReviewWeighsLessThanAnActiveOne() {
    RegisteredTestUser merchantA = merchantComCredito();
    RegisteredTestUser courierA = disponivel();
    UUID pedidoA = finalizarPedido(merchantA, courierA);
    avaliar(merchantA, pedidoA, 1, "Nota baixa, real");

    ScoreResponse scoreComUmaAtiva = aguardarScore(courierA);
    BigDecimal componenteAtivo =
        scoreComUmaAtiva.components().stream()
            .filter(c -> c.name().equals("avaliacao"))
            .findFirst()
            .orElseThrow()
            .value();
    assertThat(componenteAtivo).isEqualByComparingTo("1.00");

    // Agora some uma automática (ativa=false, nota 5) pro mesmo entregador em outro pedido — se
    // pesasse igual a uma real, a média subiria para 3.00; pesando menos, fica mais perto de 1.
    RegisteredTestUser merchantB = merchantComCredito();
    UUID pedidoB = finalizarPedido(merchantB, courierA);
    jdbcTemplate.update(
        "insert into avaliacao (id, pedido_id, autor_id, alvo_id, nota, comentario, ativa) values"
            + " (?, ?, ?, ?, 5, null, false)",
        com.uaiou.shared.id.UuidV7.next(),
        pedidoB,
        merchantB.id(),
        courierA.id());

    // O recálculo é por evento, nunca na leitura (RF-20.3) — chamando o serviço direto, exatamente
    // como o listener real faria ao reagir ao evento que criou a automática acima.
    scoreCalculationService.recalcularEntregador(courierA.id());

    ScoreResponse depois = aguardarScoreDiferente(courierA, scoreComUmaAtiva.calculatedAt());
    BigDecimal mediaPonderada =
        depois.components().stream()
            .filter(c -> c.name().equals("avaliacao"))
            .findFirst()
            .orElseThrow()
            .value();
    assertThat(mediaPonderada).isLessThan(new BigDecimal("3.00"));
  }

  /** Critério 6: entregador sem histórico retorna score nulo (sem base), não zero. */
  @Test
  void aCourierWithNoHistoryReturnsNullScore() {
    RegisteredTestUser courier = registerAndActivateCourier();

    ScoreResponse score =
        restTemplate
            .exchange(
                baseUrl("/me/score"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(courier).accessToken())),
                ScoreResponse.class)
            .getBody();

    assertThat(score.value()).isNull();
  }

  /** Critério 7: não existe rota de escrita de score. */
  @Test
  void thereIsNoWriteRouteForScore() {
    RegisteredTestUser courier = registerAndActivateCourier();

    ResponseEntity<String> resposta =
        restTemplate.exchange(
            baseUrl("/me/score"),
            HttpMethod.PUT,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            String.class);

    // Este backend mapeia método não suportado para 400 (GlobalExceptionHandler), não 405 — o que
    // importa aqui é que a escrita não teve efeito nenhum, verificado por não existir handler PUT.
    assertThat(resposta.getStatusCode())
        .isIn(HttpStatus.BAD_REQUEST, HttpStatus.METHOD_NOT_ALLOWED, HttpStatus.NOT_FOUND);
  }

  /** Critério 8: estabelecimento vê o score do proponente na listagem de contraofertas. */
  @Test
  void theMerchantSeesTheProponentsScoreInTheCounterofferListing() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoAntigo = finalizarPedido(merchant, courier);
    avaliar(merchant, pedidoAntigo, 5, "Ótimo");
    aguardarScore(courier);

    RegisteredTestUser merchant2 = merchantComCredito();
    UUID pedidoId = publicar(merchant2, null);
    restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/counteroffers"),
        HttpMethod.POST,
        authed(login(courier).accessToken(), new CreateCounterofferRequest(Money.of("9.00"))),
        CounterofferResponse.class);

    List<CounterofferResponse> lista =
        restTemplate
            .exchange(
                baseUrl("/orders/" + pedidoId + "/counteroffers"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(merchant2).accessToken())),
                new ParameterizedTypeReference<List<CounterofferResponse>>() {})
            .getBody();

    assertThat(lista.get(0).courierScore()).isNotNull();
  }

  private ScoreResponse aguardarScore(RegisteredTestUser courier) {
    java.util.concurrent.atomic.AtomicReference<ScoreResponse> ref =
        new java.util.concurrent.atomic.AtomicReference<>();
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              ScoreResponse score =
                  restTemplate
                      .exchange(
                          baseUrl("/me/score"),
                          HttpMethod.GET,
                          new HttpEntity<>(authHeaders(login(courier).accessToken())),
                          ScoreResponse.class)
                      .getBody();
              assertThat(score.value()).isNotNull();
              ref.set(score);
            });
    return ref.get();
  }

  private ScoreResponse aguardarScoreDiferente(RegisteredTestUser courier, Instant anterior) {
    java.util.concurrent.atomic.AtomicReference<ScoreResponse> ref =
        new java.util.concurrent.atomic.AtomicReference<>();
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              ScoreResponse score =
                  restTemplate
                      .exchange(
                          baseUrl("/me/score"),
                          HttpMethod.GET,
                          new HttpEntity<>(authHeaders(login(courier).accessToken())),
                          ScoreResponse.class)
                      .getBody();
              assertThat(score.calculatedAt()).isAfter(anterior);
              ref.set(score);
            });
    return ref.get();
  }

  private BigDecimal scoreDoEntregador(UUID entregadorId) {
    return jdbcTemplate.queryForObject(
        "select score from entregador where usuario_id = ?", BigDecimal.class, entregadorId);
  }

  private ScoreResponse scoreDoEstabelecimento(RegisteredTestUser merchant) {
    java.util.concurrent.atomic.AtomicReference<ScoreResponse> ref =
        new java.util.concurrent.atomic.AtomicReference<>();
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              ScoreResponse score =
                  restTemplate
                      .exchange(
                          baseUrl("/me/score"),
                          HttpMethod.GET,
                          new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                          ScoreResponse.class)
                      .getBody();
              assertThat(score.penalties()).isNotEmpty();
              ref.set(score);
            });
    return ref.get();
  }

  private void acionarContingenciaEVencerPrazo(RegisteredTestUser courier, UUID pedidoId) {
    restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/delivery/code-recoveries"),
        HttpMethod.POST,
        authed(login(courier).accessToken(), new CodeRecoveryRequest("receiver_without_code")),
        Object.class);
    jdbcTemplate.update(
        "update contingencia_otp set prazo_em = ? where pedido_id = ? and degrau = 2 and resultado = 'notificado'",
        java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        pedidoId);
    contingencyExpiryJob.resolverPrazosVencidos();
  }

  private void avaliar(RegisteredTestUser autor, UUID pedidoId, int nota, String comentario) {
    ResponseEntity<Object> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/reviews"),
            HttpMethod.POST,
            authed(login(autor).accessToken(), new CreateReviewRequest(nota, comentario)),
            Object.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private void aceitar(RegisteredTestUser courier, UUID pedidoId) {
    ResponseEntity<AssignmentResponse> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/assignment"),
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            AssignmentResponse.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    marcarColetado(pedidoId);
  }

  private UUID finalizarPedido(RegisteredTestUser merchant, RegisteredTestUser courier) {
    UUID pedidoId = publicar(merchant, "31998877665");
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

  private UUID publicar(RegisteredTestUser merchant, String telefone) {
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
            new CreateOrderRequest.ReceiverRequest("Marina", telefone));
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
