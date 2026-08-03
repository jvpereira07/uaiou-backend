package com.uaiou.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uaiou.admin.dto.AdminUserDetail;
import com.uaiou.blocks.dto.BlockCourierRequest;
import com.uaiou.blocks.dto.BlockedCourierSummary;
import com.uaiou.blocks.service.BloqueioService;
import com.uaiou.orders.dto.CreateOrderRequest;
import com.uaiou.orders.dto.DestinationRequest;
import com.uaiou.orders.dto.OrderListResponse;
import com.uaiou.orders.dto.OrderResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.shared.money.Money;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.support.OrderTestFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-12.1 a RF-12.8 — critérios de aceite de T-12. */
class BlockedCouriersIntegrationTest extends AbstractAuthIntegrationTest {

  private static final String PERTO_LAT = "-19.918200";
  private static final String PERTO_LONG = "-43.938600";

  @Autowired private OrderTestFixtures orders;
  @Autowired private BloqueioService bloqueioService;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Critério 1: bloquear remove o pedido da vitrine na chamada seguinte. */
  @Test
  void blockingRemovesTheMerchantsOrderFromTheCourierFeedImmediately() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    UUID pedidoId = publicar(merchant);
    assertThat(vitrine(courier).data()).anyMatch(o -> o.id().equals(pedidoId));

    block(merchant, courier.id(), "Atrasos recorrentes");

    assertThat(vitrine(courier).data()).noneMatch(o -> o.id().equals(pedidoId));
  }

  /** Critério 7: desbloquear devolve a visibilidade na listagem seguinte. */
  @Test
  void unblockingRestoresVisibility() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    UUID pedidoId = publicar(merchant);
    block(merchant, courier.id(), "Teste");
    assertThat(vitrine(courier).data()).noneMatch(o -> o.id().equals(pedidoId));

    ResponseEntity<Void> response =
        restTemplate.exchange(
            baseUrl("/me/blocked-couriers/" + courier.id()),
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders(login(merchant).accessToken())),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(vitrine(courier).data()).anyMatch(o -> o.id().equals(pedidoId));
  }

  /** Critério 4: par duplicado → 409. */
  @Test
  void blockingTheSameCourierTwiceIsAConflict() {
    RegisteredTestUser courier = registerAndActivateCourier();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    block(merchant, courier.id(), "Primeira vez");

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/me/blocked-couriers"),
            HttpMethod.POST,
            authed(
                login(merchant).accessToken(),
                new BlockCourierRequest(courier.id(), "Segunda vez")),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().error().code()).isEqualTo("ALREADY_BLOCKED");
  }

  /** Critério 5: bloqueio de um estabelecimento não afeta pedidos de outro (RN-07.1). */
  @Test
  void aBlockFromOneMerchantDoesNotAffectAnother() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser bloqueador = registerAndActivateMerchant();
    RegisteredTestUser outro = registerAndActivateMerchant();
    orders.darCreditos(bloqueador.id(), 5);
    orders.darCreditos(outro.id(), 5);
    UUID doBloqueador = publicar(bloqueador);
    UUID doOutro = publicar(outro);

    block(bloqueador, courier.id(), "Só desta loja");

    OrderListResponse vitrine = vitrine(courier);
    assertThat(vitrine.data()).noneMatch(o -> o.id().equals(doBloqueador));
    assertThat(vitrine.data()).anyMatch(o -> o.id().equals(doOutro));
  }

  /** Critério 3: bloquear não altera pedido já atribuído (RF-12.4). */
  @Test
  void blockingDoesNotTouchAnAlreadyAssignedOrder() {
    RegisteredTestUser courier = disponivel();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    orders.darCreditos(merchant.id(), 5);
    UUID pedidoId = publicar(merchant);
    // Aceite é T-13; o estado "aceito" é montado direto no banco.
    jdbcTemplate.update(
        "update pedido set status = 'aceito', entregador_id = ?, frete_final = frete_proposto,"
            + " aceito_em = now() where id = ?",
        courier.id(),
        pedidoId);

    block(merchant, courier.id(), "Depois do aceite");

    String status =
        jdbcTemplate.queryForObject(
            "select status from pedido where id = ?", String.class, pedidoId);
    UUID entregador =
        jdbcTemplate.queryForObject(
            "select entregador_id from pedido where id = ?", UUID.class, pedidoId);
    assertThat(status).isEqualTo("aceito");
    assertThat(entregador).isEqualTo(courier.id());
  }

  /**
   * Critério 2: entregador bloqueado que tenta aceitar → 403 {@code BLOCKED_BY_MERCHANT}. A rota de
   * aceite é de T-13 e ainda não existe; o que T-12 entrega é a guarda que ela vai chamar,
   * exercitada aqui diretamente (mesmo padrão de CreditWalletService antes de T-11 existir).
   */
  @Test
  void theGuardUsedByAcceptAndCounterofferRejectsABlockedCourier() {
    RegisteredTestUser courier = registerAndActivateCourier();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    block(merchant, courier.id(), "Bloqueado");

    assertThatThrownBy(() -> bloqueioService.requireNotBlocked(merchant.id(), courier.id()))
        .isInstanceOf(ForbiddenException.class)
        .satisfies(
            e -> assertThat(((ForbiddenException) e).code()).isEqualTo("BLOCKED_BY_MERCHANT"));
  }

  @Test
  void theGuardLetsAnUnblockedCourierThrough() {
    RegisteredTestUser courier = registerAndActivateCourier();
    RegisteredTestUser merchant = registerAndActivateMerchant();

    bloqueioService.requireNotBlocked(merchant.id(), courier.id());
  }

  @Test
  void listingShowsNameReasonAndDate() {
    RegisteredTestUser courier = registerAndActivateCourier();
    RegisteredTestUser merchant = registerAndActivateMerchant();
    block(merchant, courier.id(), "Entrega fora do prazo");

    var lista =
        restTemplate
            .exchange(
                baseUrl("/me/blocked-couriers"),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(login(merchant).accessToken())),
                new ParameterizedTypeReference<java.util.List<BlockedCourierSummary>>() {})
            .getBody();

    assertThat(lista).hasSize(1);
    assertThat(lista.get(0).courierId()).isEqualTo(courier.id());
    assertThat(lista.get(0).courierName()).isNotBlank();
    assertThat(lista.get(0).reason()).isEqualTo("Entrega fora do prazo");
    assertThat(lista.get(0).blockedAt()).isNotNull();
  }

  /** RF-12.7 — sinal agregado por estabelecimentos DISTINTOS, exposto ao admin. */
  @Test
  void theAdminDossierShowsHowManyDistinctMerchantsBlockedTheCourier() {
    RegisteredTestUser courier = registerAndActivateCourier();
    RegisteredTestUser a = registerAndActivateMerchant();
    RegisteredTestUser b = registerAndActivateMerchant();
    block(a, courier.id(), "um");
    block(b, courier.id(), "dois");

    AdminSession admin = loginAdmin();
    AdminUserDetail detail =
        restTemplate
            .exchange(
                baseUrl("/admin/users/" + courier.id()),
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(admin.accessToken())),
                AdminUserDetail.class)
            .getBody();

    assertThat(detail.blockedByMerchantCount()).isEqualTo(2L);
  }

  @Test
  void aCourierCannotUseTheMerchantBlockRoutes() {
    RegisteredTestUser courier = registerAndActivateCourier();

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/me/blocked-couriers"),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(courier).accessToken())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().error().code()).isEqualTo("MERCHANT_ONLY");
  }

  private void block(RegisteredTestUser merchant, UUID courierId, String reason) {
    ResponseEntity<BlockedCourierSummary> response =
        restTemplate.exchange(
            baseUrl("/me/blocked-couriers"),
            HttpMethod.POST,
            authed(login(merchant).accessToken(), new BlockCourierRequest(courierId, reason)),
            BlockedCourierSummary.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
