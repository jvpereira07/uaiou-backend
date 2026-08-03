package com.uaiou.presence;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.presence.service.CourierPresenceService;
import com.uaiou.presence.service.PresenceExpiryJob;
import com.uaiou.support.AbstractAuthIntegrationTest;
import com.uaiou.users.entity.Entregador;
import com.uaiou.users.repository.EntregadorRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** RF-10.4 a RF-10.9 — critérios de aceite 4, 5 e 6 de T-10. */
class EligibilityAndExpiryIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private CourierPresenceService courierPresenceService;
  @Autowired private PresenceExpiryJob presenceExpiryJob;
  @Autowired private EntregadorRepository entregadorRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redis;

  @Test
  void locationUpdateWritesThePositionAndAccuracyToTheDatabase() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();

    reportLocation(token, "-19.918200", "-43.938600", "12.5");

    Entregador entregador = entregadorRepository.findById(courier.id()).orElseThrow();
    assertThat(entregador.getLat()).isEqualByComparingTo("-19.918200");
    assertThat(entregador.getLongitude()).isEqualByComparingTo("-43.938600");
    assertThat(entregador.getAccuracy()).isEqualByComparingTo("12.5");
    assertThat(entregador.getLocalizacaoEm()).isNotNull();
  }

  @Test
  void lastPositionOverwritesInsteadOfAccumulatingHistory() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();

    reportLocation(token, "-19.900000", "-43.900000", "20.0");
    reportLocation(token, "-19.910000", "-43.910000", "8.0");

    // RF-10.5: só a última posição existe — não há tabela de trajeto para consultar.
    Entregador entregador = entregadorRepository.findById(courier.id()).orElseThrow();
    assertThat(entregador.getLat()).isEqualByComparingTo("-19.910000");
    assertThat(entregador.getAccuracy()).isEqualByComparingTo("8.0");
  }

  @Test
  void anAvailableCourierWithAFreshPositionIsEligible() {
    RegisteredTestUser courier = becomeAvailableCourier();

    List<CourierPresence> eligible = courierPresenceService.findEligibleCouriers();

    assertThat(eligible).anyMatch(p -> p.courierId().equals(courier.id()));
  }

  @Test
  void anUnavailableCourierIsNotEligibleEvenWithAFreshPosition() {
    RegisteredTestUser courier = becomeAvailableCourier();
    String token = login(courier).accessToken();
    setAvailability(token, false);

    List<CourierPresence> eligible = courierPresenceService.findEligibleCouriers();

    assertThat(eligible).noneMatch(p -> p.courierId().equals(courier.id()));
  }

  /**
   * Critério de aceite 6: posição velha é ignorada mesmo com {@code disponivel = true}. Envelhece a
   * linha no banco E remove a réplica do Redis — é exatamente o estado que o TTL produz quando a
   * chave expira antes do job de reconciliação rodar.
   */
  @Test
  void aStalePositionIsIgnoredEvenWhenTheCourierIsStillFlaggedAvailable() {
    RegisteredTestUser courier = becomeAvailableCourier();
    ageLocation(courier.id(), 30);
    expireCacheEntry(courier.id());

    List<CourierPresence> eligible = courierPresenceService.findEligibleCouriers();

    assertThat(eligible).noneMatch(p -> p.courierId().equals(courier.id()));
    // A flag no banco ainda está true — quem excluiu foi o corte por frescor, não o job.
    assertThat(entregadorRepository.findById(courier.id()).orElseThrow().isDisponivel()).isTrue();
  }

  /** Critério de aceite 5: quem para de reportar posição é desligado pelo job. */
  @Test
  void theExpiryJobTurnsOffCouriersThatStoppedReportingPosition() {
    RegisteredTestUser courier = becomeAvailableCourier();
    ageLocation(courier.id(), 30);

    presenceExpiryJob.desligarPresencasVencidas();

    Entregador entregador = entregadorRepository.findById(courier.id()).orElseThrow();
    assertThat(entregador.isDisponivel()).isFalse();
    assertThat(entregador.getDisponivelDesde()).isNull();
    assertThat(courierPresenceService.findEligibleCouriers())
        .noneMatch(p -> p.courierId().equals(courier.id()));
  }

  @Test
  void theExpiryJobLeavesCouriersWithAFreshPositionAlone() {
    RegisteredTestUser courier = becomeAvailableCourier();

    presenceExpiryJob.desligarPresencasVencidas();

    assertThat(entregadorRepository.findById(courier.id()).orElseThrow().isDisponivel()).isTrue();
  }

  private RegisteredTestUser becomeAvailableCourier() {
    RegisteredTestUser courier = registerAndActivateCourier();
    String token = login(courier).accessToken();
    reportLocation(token, "-19.918200", "-43.938600", "12.5");
    setAvailability(token, true);
    return courier;
  }

  private void reportLocation(String token, String lat, String lng, String accuracy) {
    var response =
        restTemplate.exchange(
            baseUrl("/me/location"),
            HttpMethod.PUT,
            authed(
                token,
                new UpdateLocationRequest(
                    new BigDecimal(lat), new BigDecimal(lng), new BigDecimal(accuracy))),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  private void setAvailability(String token, boolean available) {
    restTemplate.exchange(
        baseUrl("/me/availability"),
        HttpMethod.PUT,
        authed(token, new UpdateAvailabilityRequest(available)),
        Object.class);
  }

  private void ageLocation(UUID courierId, int minutes) {
    jdbcTemplate.update(
        "update entregador set localizacao_em = ? where usuario_id = ?",
        Timestamp.from(Instant.now().minus(minutes, ChronoUnit.MINUTES)),
        courierId);
  }

  /** Simula o vencimento do TTL sem esperar o relógio: apaga a chave de posição no Redis. */
  private void expireCacheEntry(UUID courierId) {
    redis.delete("presenca:pos:" + courierId);
  }
}
