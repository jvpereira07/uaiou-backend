package com.uaiou.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.uaiou.presence.dto.AvailabilityResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.presence.service.CourierPresenceService;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Critério de aceite 7 de T-10: queda do Redis é <strong>degradação, não erro</strong> — a listagem
 * continua correta (vinda do PostgreSQL) e as rotas de escrita continuam respondendo 200/204.
 *
 * <p>Substitui o {@link StringRedisTemplate} por um mock que estoura em toda operação, em vez de
 * derrubar o container: assim o teste exercita o {@code try/catch} real do {@code PresenceCache} de
 * forma determinística, sem depender de timeout de rede.
 */
class PresenceCacheDegradationIntegrationTest extends AbstractAuthIntegrationTest {

  @MockitoBean private StringRedisTemplate redis;

  @Autowired private CourierPresenceService courierPresenceService;

  @BeforeEach
  void redisIsDown() {
    RedisConnectionFailureException failure =
        new RedisConnectionFailureException("Redis fora do ar (simulado)");
    given(redis.opsForValue()).willThrow(failure);
    given(redis.opsForSet()).willThrow(failure);
    willThrow(failure).given(redis).delete(anyString());
  }

  @Test
  void goingAvailableStillSucceedsWhenRedisIsDown() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();

    ResponseEntity<Void> location =
        restTemplate.exchange(
            baseUrl("/me/location"),
            HttpMethod.PUT,
            authed(
                token,
                new UpdateLocationRequest(
                    new BigDecimal("-19.918200"),
                    new BigDecimal("-43.938600"),
                    new BigDecimal("12.5"))),
            Void.class);
    assertThat(location.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<AvailabilityResponse> availability =
        restTemplate.exchange(
            baseUrl("/me/availability"),
            HttpMethod.PUT,
            authed(token, new UpdateAvailabilityRequest(true)),
            AvailabilityResponse.class);

    assertThat(availability.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(availability.getBody().available()).isTrue();
  }

  @Test
  void eligibilityListingFallsBackToPostgresWhenRedisIsDown() {
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
                new BigDecimal("12.5"))),
        Void.class);
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(true)),
        Object.class);

    // Nada foi para o cache (ele estourou em toda escrita) — mesmo assim a listagem tem que estar
    // correta, porque a fonte de verdade é o banco.
    List<CourierPresence> eligible = courierPresenceService.findEligibleCouriers();

    assertThat(eligible).anyMatch(p -> p.courierId().equals(courier.id()));
    assertThat(eligible)
        .filteredOn(p -> p.courierId().equals(courier.id()))
        .allSatisfy(
            p -> {
              assertThat(p.lat()).isEqualByComparingTo("-19.918200");
              assertThat(p.accuracy()).isEqualByComparingTo("12.5");
            });
  }
}
