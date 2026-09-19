package com.uaiou.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** Gestão de entregas pelo painel admin: intervenção manual no estado, histórico e timeouts. */
class AdminOrderManagementIntegrationTest extends AbstractAuthIntegrationTest {

  private static final String DEST_LAT = "-19.925100";
  private static final String DEST_LONG = "-43.941700";
  private static final String LOJA_LAT = "-19.930000";
  private static final String LOJA_LONG = "-43.935000";
  private static final String PERTO_LAT = "-19.918200";
  private static final String PERTO_LONG = "-43.938600";

  private static final ParameterizedTypeReference<Map<String, Object>> JSON =
      new ParameterizedTypeReference<>() {};

  @Autowired private OrderTestFixtures orders;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void adminCancelsAPickedUpOrderAuditsItAndNotifiesBothSides() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchant);
    aceitar(courier, pedidoId);
    marcarColetado(pedidoId);
    AdminSession admin = loginAdmin();

    ResponseEntity<Map<String, Object>> resposta =
        agir(admin, pedidoId, "cancel", "Pacote extraviado, confirmado com a loja.");

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody()).containsEntry("status", "cancelled");
    assertThat((List<?>) resposta.getBody().get("availableActions")).isEmpty();
    assertThat(orders.statusPersistidoDe(pedidoId)).isEqualTo("cancelado");
    assertThat(
            jdbcTemplate.queryForObject(
                "select cancelamento_motivo from pedido where id = ?", String.class, pedidoId))
        .isEqualTo("admin");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from registro_auditoria where acao = 'order_cancel'"
                    + " and referencia_id = ?",
                Integer.class,
                pedidoId))
        .isEqualTo(1);
    assertThat(lancamentosDe(pedidoId)).as("intervenção da plataforma não cobra taxa").isZero();
    assertThat(notificacoesDoTipo(courier.id(), "order.cancelled")).isEqualTo(1);
    assertThat(notificacoesDoTipo(merchant.id(), "order.cancelled")).isEqualTo(1);
  }

  @Test
  void returningToShowcaseClearsTheAssignmentAndTheDeliveryCode() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchant);
    aceitar(courier, pedidoId);
    marcarColetado(pedidoId);

    ResponseEntity<Map<String, Object>> resposta =
        agir(loginAdmin(), pedidoId, "return_to_showcase", "Entregador sem contato.");

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody()).containsEntry("status", "published");
    Map<String, Object> linha =
        jdbcTemplate.queryForMap(
            "select entregador_id, coletado_em, frete_final from pedido where id = ?", pedidoId);
    assertThat(linha.get("entregador_id")).isNull();
    assertThat(linha.get("coletado_em")).isNull();
    assertThat(linha.get("frete_final")).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from otp where pedido_id = ?", Integer.class, pedidoId))
        .isZero();
  }

  @Test
  void finalizingManuallyCreatesTheLedgerEntry() {
    RegisteredTestUser merchant = merchantComCoordenada();
    RegisteredTestUser courier = disponivel();
    UUID pedidoId = publicar(merchant);
    aceitar(courier, pedidoId);
    marcarColetado(pedidoId);

    ResponseEntity<Map<String, Object>> resposta =
        agir(loginAdmin(), pedidoId, "finalize", "Entrega confirmada por telefone.");

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resposta.getBody()).containsEntry("status", "finalized");
    assertThat(lancamentosDe(pedidoId)).isEqualTo(1);
  }

  @Test
  void anActionOutsideItsOriginStatesIsRefused() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicar(merchant);

    ResponseEntity<ErrorResponse> resposta =
        restTemplate.exchange(
            baseUrl("/admin/orders/" + pedidoId + "/actions"),
            HttpMethod.POST,
            authed(
                loginAdmin().accessToken(),
                Map.of("action", "finalize", "reason", "Tentativa inválida.")),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(resposta.getBody().error().code()).isEqualTo("ORDER_ACTION_NOT_ALLOWED");
    assertThat(orders.statusPersistidoDe(pedidoId)).isEqualTo("publicado");
  }

  @Test
  void theHistoryListsOrdersFilteredByMerchantAndStatus() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicar(merchant);

    ResponseEntity<Map<String, Object>> resposta =
        restTemplate.exchange(
            baseUrl("/admin/orders?status=published,in_negotiation&merchantId=" + merchant.id()),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(loginAdmin().accessToken())),
            JSON);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<?> data = (List<?>) resposta.getBody().get("data");
    assertThat(data).hasSize(1);
    assertThat(((Map<?, ?>) data.get(0)).get("id")).isEqualTo(pedidoId.toString());
  }

  @Test
  void anEnabledTimeoutCancelsAStaleUnacceptedOrderAndRecordsTheOccurrence() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicar(merchant);
    jdbcTemplate.update(
        "update pedido set criado_em = now() - interval '10 minutes' where id = ?", pedidoId);
    AdminSession admin = loginAdmin();

    try {
      ResponseEntity<Map<String, Object>> ajuste =
          restTemplate.exchange(
              baseUrl("/admin/timeouts/unaccepted"),
              HttpMethod.PUT,
              authed(admin.accessToken(), Map.of("minutes", 5, "active", true)),
              JSON);
      assertThat(ajuste.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(ajuste.getBody()).containsEntry("active", true).containsEntry("minutes", 5);

      ResponseEntity<Map<String, Object>> execucao =
          restTemplate.exchange(
              baseUrl("/admin/timeouts/run"),
              HttpMethod.POST,
              new HttpEntity<>(authHeaders(admin.accessToken())),
              JSON);
      assertThat(execucao.getStatusCode()).isEqualTo(HttpStatus.OK);
    } finally {
      jdbcTemplate.update(
          "update configuracao_timeout set ativo = false, duracao_minutos = 120"
              + " where chave = 'publicado_sem_aceite'");
    }

    assertThat(orders.statusPersistidoDe(pedidoId)).isEqualTo("cancelado");
    assertThat(
            jdbcTemplate.queryForObject(
                "select cancelamento_motivo from pedido where id = ?", String.class, pedidoId))
        .isEqualTo("timeout");
    assertThat(
            jdbcTemplate.queryForObject(
                "select acao from ocorrencia_timeout where pedido_id = ?", String.class, pedidoId))
        .isEqualTo("cancelado");
  }

  @Test
  void aNonAdminCannotChangeAnOrder() {
    RegisteredTestUser merchant = merchantComCoordenada();
    UUID pedidoId = publicar(merchant);

    ResponseEntity<ErrorResponse> resposta =
        restTemplate.exchange(
            baseUrl("/admin/orders/" + pedidoId + "/actions"),
            HttpMethod.POST,
            authed(
                login(merchant).accessToken(),
                Map.of("action", "cancel", "reason", "Tentando pelo painel.")),
            ErrorResponse.class);

    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  private ResponseEntity<Map<String, Object>> agir(
      AdminSession admin, UUID pedidoId, String acao, String motivo) {
    return restTemplate.exchange(
        baseUrl("/admin/orders/" + pedidoId + "/actions"),
        HttpMethod.POST,
        authed(admin.accessToken(), Map.of("action", acao, "reason", motivo)),
        JSON);
  }

  private void aceitar(RegisteredTestUser courier, UUID pedidoId) {
    ResponseEntity<Object> resposta =
        restTemplate.exchange(
            baseUrl("/orders/" + pedidoId + "/assignment"),
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            Object.class);
    assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private int lancamentosDe(UUID pedidoId) {
    return jdbcTemplate.queryForObject(
        "select count(*) from lancamento_frete where pedido_id = ?", Integer.class, pedidoId);
  }

  private int notificacoesDoTipo(UUID usuarioId, String tipo) {
    return jdbcTemplate.queryForObject(
        "select count(*) from notificacao where usuario_id = ? and tipo = ?",
        Integer.class,
        usuarioId,
        tipo);
  }

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
        Object.class);
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
            Money.of("9.00"),
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
}
