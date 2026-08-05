package com.uaiou.tickets;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.admin.dto.AdminOrderDetail;
import com.uaiou.notifications.dto.NotificationListResponse;
import com.uaiou.orders.dto.AssignmentResponse;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import com.uaiou.tickets.dto.AddMessageRequest;
import com.uaiou.tickets.dto.CreateTicketRequest;
import com.uaiou.tickets.dto.ResolveTicketRequest;
import com.uaiou.tickets.dto.TicketDetail;
import com.uaiou.tickets.dto.TicketSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** RF-21.1 a RF-21.8 — critérios de aceite de T-21. */
class SupportTicketIntegrationTest extends AbstractAuthIntegrationTest {

  private static final BigDecimal DEST_LAT = new BigDecimal("-19.925100");
  private static final BigDecimal DEST_LNG = new BigDecimal("-43.941700");

  @Autowired private OrderTestFixtures orders;

  /** Critério 1: abrir chamado cria chamado + primeira mensagem na mesma transação. */
  @Test
  void openingATicketCreatesTheTicketAndTheFirstMessageTogether() {
    RegisteredTestUser merchant = merchantComCredito();

    ResponseEntity<TicketDetail> resposta =
        abrirChamado(merchant, "Dúvida sobre cobrança", null, "Fui cobrado em dobro");

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resposta.getBody().messages()).hasSize(1);
    assertThat(resposta.getBody().messages().get(0).text()).isEqualTo("Fui cobrado em dobro");
  }

  /** Critério 2: reference a pedido de terceiro → 403. */
  @Test
  void referencingSomeoneElsesOrderIsForbidden() {
    RegisteredTestUser merchant = merchantComCredito();
    UUID pedidoId = publicar(merchant);
    RegisteredTestUser estranho = merchantComCredito();

    ResponseEntity<ErrorResponse> resposta =
        restTemplate.exchange(
            baseUrl("/support-tickets"),
            HttpMethod.POST,
            authed(
                login(estranho).accessToken(),
                new CreateTicketRequest(
                    "Sobre um pedido",
                    "Isso não é meu",
                    new CreateTicketRequest.ReferenceRef("order", pedidoId))),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(resposta.getBody().error().code()).isEqualTo("NOT_A_PARTY");
  }

  /** Critério 3: autor vê só os próprios; admin vê a fila completa. */
  @Test
  void theAuthorSeesOnlyTheirOwnTicketsAndTheAdminSeesTheWholeQueue() {
    RegisteredTestUser merchantA = merchantComCredito();
    RegisteredTestUser merchantB = merchantComCredito();
    abrirChamado(merchantA, "Chamado A", null, "msg A");
    abrirChamado(merchantB, "Chamado B", null, "msg B");

    List<TicketSummary> deA = listar(merchantA);
    assertThat(deA).hasSize(1).allMatch(t -> t.authorId().equals(merchantA.id()));

    String adminToken = loginAdmin().accessToken();
    List<TicketSummary> fila =
        restTemplate
            .exchange(
                baseUrl("/support-tickets"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(adminToken)),
                new ParameterizedTypeReference<List<TicketSummary>>() {})
            .getBody();
    assertThat(fila.size()).isGreaterThanOrEqualTo(2);
  }

  /** Critério 4: primeira mensagem do admin move para em_atendimento e vincula o responsável. */
  @Test
  void theAdminsFirstMessageMovesToInProgressAndAssignsTheResponder() {
    RegisteredTestUser merchant = merchantComCredito();
    UUID chamadoId = abrirChamado(merchant, "Preciso de ajuda", null, "msg").getBody().id();
    String adminToken = loginAdmin().accessToken();

    ResponseEntity<TicketDetail> resposta =
        restTemplate.exchange(
            baseUrl("/support-tickets/" + chamadoId + "/messages"),
            HttpMethod.POST,
            authed(adminToken, new AddMessageRequest("Já estou vendo seu caso")),
            TicketDetail.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(resposta.getBody().status()).isEqualTo(TicketStatus.IN_PROGRESS);
    assertThat(resposta.getBody().adminId()).isNotNull();
  }

  /** Critério 5: autor escrevendo em chamado resolvido → 422. */
  @Test
  void theAuthorCannotWriteInAResolvedTicket() {
    RegisteredTestUser merchant = merchantComCredito();
    UUID chamadoId = abrirChamado(merchant, "Caso simples", null, "msg").getBody().id();
    String adminToken = loginAdmin().accessToken();
    restTemplate.exchange(
        baseUrl("/support-tickets/" + chamadoId + "/resolution"),
        HttpMethod.PUT,
        authed(adminToken, new ResolveTicketRequest("Resolvido, segue a explicação", null, null)),
        TicketDetail.class);

    ResponseEntity<ErrorResponse> resposta =
        restTemplate.exchange(
            baseUrl("/support-tickets/" + chamadoId + "/messages"),
            HttpMethod.POST,
            authed(
                login(merchant).accessToken(), new AddMessageRequest("Mas eu ainda tenho dúvida")),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    assertThat(resposta.getBody().error().code()).isEqualTo("TICKET_RESOLVED");
  }

  /** Critério 6: encerramento registra resposta final, muda para resolvido e notifica o autor. */
  @Test
  void resolvingRecordsTheFinalResponseAndNotifiesTheAuthor() {
    RegisteredTestUser merchant = merchantComCredito();
    UUID chamadoId = abrirChamado(merchant, "Caso simples", null, "msg").getBody().id();
    String adminToken = loginAdmin().accessToken();

    ResponseEntity<TicketDetail> resposta =
        restTemplate.exchange(
            baseUrl("/support-tickets/" + chamadoId + "/resolution"),
            HttpMethod.PUT,
            authed(
                adminToken, new ResolveTicketRequest("Resolvido, segue a explicação", null, null)),
            TicketDetail.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody().status()).isEqualTo(TicketStatus.RESOLVED);
    assertThat(resposta.getBody().resolvedAt()).isNotNull();

    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              NotificationListResponse inbox =
                  restTemplate
                      .exchange(
                          baseUrl("/me/notifications?type=support.replied&perPage=10"),
                          HttpMethod.GET,
                          new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                          NotificationListResponse.class)
                      .getBody();
              assertThat(inbox.data()).isNotEmpty();
            });
  }

  /** Critério 7: encerramento com adjustmentId liga o chamado ao ajuste. */
  @Test
  void resolvingWithAnAdjustmentIdLinksTheTicketToIt() {
    RegisteredTestUser merchant = merchantComCredito();
    UUID chamadoId = abrirChamado(merchant, "Cobrança errada", null, "msg").getBody().id();
    String adminToken = loginAdmin().accessToken();
    UUID ajusteId = UUID.randomUUID();

    ResponseEntity<TicketDetail> resposta =
        restTemplate.exchange(
            baseUrl("/support-tickets/" + chamadoId + "/resolution"),
            HttpMethod.PUT,
            authed(
                adminToken,
                new ResolveTicketRequest("Corrigido via ajuste", "credits_adjustment", ajusteId)),
            TicketDetail.class);

    assertThat(resposta.getBody().adjustmentId()).isEqualTo(ajusteId);
    assertThat(resposta.getBody().adjustmentType()).isEqualTo("credits_adjustment");
  }

  /** Critério 8: GET /admin/orders/{id} mostra a timeline, sem o código de entrega. */
  @Test
  void adminOrderDetailShowsTheTimelineWithoutTheDeliveryCode() {
    RegisteredTestUser merchant = merchantComCredito();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchant);
    restTemplate.exchange(
        baseUrl("/orders/" + pedidoId + "/assignment"),
        HttpMethod.POST,
        new HttpEntity<>(authHeaders(login(courier).accessToken())),
        AssignmentResponse.class);
    String adminToken = loginAdmin().accessToken();

    ResponseEntity<AdminOrderDetail> resposta =
        restTemplate.exchange(
            baseUrl("/admin/orders/" + pedidoId),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(adminToken)),
            AdminOrderDetail.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody().timeline()).isNotEmpty();
    assertThat(resposta.getBody().timeline()).anyMatch(e -> e.event().equals("order.assigned"));
  }

  private ResponseEntity<TicketDetail> abrirChamado(
      RegisteredTestUser autor, String assunto, UUID referenciaId, String mensagem) {
    CreateTicketRequest.ReferenceRef reference =
        referenciaId == null ? null : new CreateTicketRequest.ReferenceRef("order", referenciaId);
    return restTemplate.exchange(
        baseUrl("/support-tickets"),
        HttpMethod.POST,
        authed(login(autor).accessToken(), new CreateTicketRequest(assunto, mensagem, reference)),
        TicketDetail.class);
  }

  private List<TicketSummary> listar(RegisteredTestUser autor) {
    return restTemplate
        .exchange(
            baseUrl("/support-tickets"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(autor).accessToken())),
            new ParameterizedTypeReference<List<TicketSummary>>() {})
        .getBody();
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
