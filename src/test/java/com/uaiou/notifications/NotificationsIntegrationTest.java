package com.uaiou.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uaiou.notifications.dto.DeviceResponse;
import com.uaiou.notifications.dto.NotificationListResponse;
import com.uaiou.notifications.dto.NotificationPreferencesResponse;
import com.uaiou.notifications.dto.NotificationSummary;
import com.uaiou.notifications.dto.RegisterDeviceRequest;
import com.uaiou.notifications.dto.UpdateNotificationPreferencesRequest;
import com.uaiou.notifications.repository.DispositivoRepository;
import com.uaiou.notifications.service.NotificationService;
import com.uaiou.shared.error.ErrorResponse;
import com.uaiou.support.AbstractAuthIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

/** RF-08.2 a RF-08.9 — critérios de aceite 1 a 6 de T-08. */
class NotificationsIntegrationTest extends AbstractAuthIntegrationTest {

  @Autowired private NotificationService notificationService;
  @Autowired private DispositivoRepository dispositivoRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  /**
   * Critério 2: push indisponível não impede a linha no inbox (o LoggingPushSender não entrega
   * nada).
   */
  @Test
  void aPublishedNotificationLandsInTheInboxEvenWithoutWorkingPush() {
    RegisteredTestUser user = registerAndActivateCourier();
    notificationService.publicar(
        user.id(),
        NotificationType.ORDER_PUBLISHED,
        "Novo pedido",
        "Corpo",
        Map.of("orderId", "x"));

    NotificationListResponse inbox = inbox(user, "");

    assertThat(inbox.data()).hasSize(1);
    assertThat(inbox.data().get(0).type()).isEqualTo("order.published");
    assertThat(inbox.data().get(0).payload()).containsEntry("orderId", "x");
    assertThat(inbox.meta().unread()).isEqualTo(1);
  }

  /**
   * Critério 1: notificação criada em transação que depois falha NÃO é publicada. Aqui o próprio
   * publicar participa de uma transação que é revertida — é o que garante que só publicar após o
   * commit (RF-08.1) é seguro.
   */
  @Test
  void aNotificationFromARolledBackTransactionNeverAppears() {
    RegisteredTestUser user = registerAndActivateCourier();

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      notificationService.publicar(
                          user.id(),
                          NotificationType.ORDER_PUBLISHED,
                          "Não deveria sobreviver",
                          "Corpo",
                          Map.of());
                      throw new IllegalStateException("falha de negócio simulada");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(inbox(user, "").data()).isEmpty();
  }

  /** Critério 5: meta.unread bate com a contagem real; marcar zera o item e o global zera todos. */
  @Test
  void unreadCountAndMarkingAsReadBehave() {
    RegisteredTestUser user = registerAndActivateCourier();
    publicar(user, "Um");
    publicar(user, "Dois");
    publicar(user, "Três");
    assertThat(inbox(user, "").meta().unread()).isEqualTo(3);

    UUID primeiro = inbox(user, "").data().get(0).id();
    ResponseEntity<NotificationSummary> lida =
        restTemplate.exchange(
            baseUrl("/me/notifications/" + primeiro + "/read"),
            HttpMethod.PUT,
            new HttpEntity<>(authHeaders(login(user).accessToken())),
            NotificationSummary.class);
    assertThat(lida.getBody().readAt()).isNotNull();
    assertThat(inbox(user, "").meta().unread()).isEqualTo(2);
    assertThat(inbox(user, "?unread=true").data()).hasSize(2);

    restTemplate.exchange(
        baseUrl("/me/notifications/read"),
        HttpMethod.PUT,
        new HttpEntity<>(authHeaders(login(user).accessToken())),
        Object.class);
    assertThat(inbox(user, "").meta().unread()).isZero();
  }

  @Test
  void anotherUsersNotificationIsNotFound() {
    RegisteredTestUser dono = registerAndActivateCourier();
    RegisteredTestUser estranho = registerAndActivateCourier();
    publicar(dono, "Privada");
    UUID id = inbox(dono, "").data().get(0).id();

    ResponseEntity<ErrorResponse> response =
        restTemplate.exchange(
            baseUrl("/me/notifications/" + id + "/read"),
            HttpMethod.PUT,
            new HttpEntity<>(authHeaders(login(estranho).accessToken())),
            ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** Critério 3: token já existente não cria segunda linha e reassocia ao usuário atual. */
  @Test
  void registeringTheSameTokenTwiceReassociatesInsteadOfDuplicating() {
    RegisteredTestUser primeiro = registerAndActivateCourier();
    RegisteredTestUser segundo = registerAndActivateCourier();
    String token = "fcm:" + UUID.randomUUID();

    ResponseEntity<DeviceResponse> a = registerDevice(primeiro, token);
    ResponseEntity<DeviceResponse> b = registerDevice(segundo, token);

    assertThat(a.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(b.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // Mesma linha (mesmo id), agora do segundo usuário — não duas linhas com o mesmo token.
    assertThat(b.getBody().id()).isEqualTo(a.getBody().id());
    assertThat(dispositivoRepository.findByPushToken(token).orElseThrow().getUsuarioId())
        .isEqualTo(segundo.id());
    assertThat(dispositivoRepository.findByUsuarioId(primeiro.id())).isEmpty();
  }

  @Test
  void aDeviceCanBeRemovedByItsOwnerOnly() {
    RegisteredTestUser dono = registerAndActivateCourier();
    RegisteredTestUser estranho = registerAndActivateCourier();
    UUID deviceId = registerDevice(dono, "fcm:" + UUID.randomUUID()).getBody().id();

    ResponseEntity<ErrorResponse> alheio =
        restTemplate.exchange(
            baseUrl("/me/devices/" + deviceId),
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders(login(estranho).accessToken())),
            ErrorResponse.class);
    assertThat(alheio.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    ResponseEntity<Void> proprio =
        restTemplate.exchange(
            baseUrl("/me/devices/" + deviceId),
            HttpMethod.DELETE,
            new HttpEntity<>(authHeaders(login(dono).accessToken())),
            Void.class);
    assertThat(proprio.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
  }

  /**
   * Critério 6: preferência não desliga transacional crítico. Silenciar o push realmente para o
   * push de um evento comum, mas NUNCA impede a linha no inbox — que é a fonte de verdade
   * (RF-08.2).
   */
  @Test
  void silencingPushNeverSilencesTheInboxAndNeverReachesMandatoryEvents() {
    RegisteredTestUser user = registerAndActivateCourier();

    ResponseEntity<NotificationPreferencesResponse> prefs =
        restTemplate.exchange(
            baseUrl("/me/notification-preferences"),
            HttpMethod.PUT,
            authed(
                login(user).accessToken(),
                new UpdateNotificationPreferencesRequest(
                    new UpdateNotificationPreferencesRequest.Channels(false, null, null))),
            NotificationPreferencesResponse.class);

    assertThat(prefs.getBody().channels()).containsEntry("push", false);
    // Os críticos aparecem como não-desligáveis para a interface não oferecer um botão inócuo.
    assertThat(prefs.getBody().mandatory())
        .contains("delivery.code_contingency", "sanction.applied", "dispute.opened");

    notificationService.publicar(
        user.id(), NotificationType.ORDER_PUBLISHED, "Comum", "Corpo", Map.of());
    notificationService.publicar(
        user.id(), NotificationType.SANCTION_APPLIED, "Crítica", "Corpo", Map.of());

    // Push silenciado, inbox intacto: os dois eventos continuam lá.
    assertThat(inbox(user, "").data()).hasSize(2);
  }

  @Test
  void theCatalogMarksUrgentEventsAsUrgent() {
    RegisteredTestUser user = registerAndActivateCourier();
    notificationService.publicar(
        user.id(), NotificationType.DELIVERY_CODE_CONTINGENCY, "Urgente", "Corpo", Map.of());

    assertThat(inbox(user, "").data().get(0).priority()).isEqualTo(NotificationPriority.URGENT);
  }

  @Test
  void theInboxCanBeFilteredByType() {
    RegisteredTestUser user = registerAndActivateCourier();
    notificationService.publicar(
        user.id(), NotificationType.ORDER_PUBLISHED, "A", "Corpo", Map.of());
    notificationService.publicar(
        user.id(), NotificationType.SUPPORT_REPLIED, "B", "Corpo", Map.of());

    assertThat(inbox(user, "?type=support.replied").data()).hasSize(1);
  }

  private void publicar(RegisteredTestUser user, String titulo) {
    notificationService.publicar(
        user.id(), NotificationType.ORDER_PUBLISHED, titulo, "Corpo", Map.of());
  }

  private ResponseEntity<DeviceResponse> registerDevice(RegisteredTestUser user, String token) {
    return restTemplate.exchange(
        baseUrl("/me/devices"),
        HttpMethod.POST,
        authed(login(user).accessToken(), new RegisterDeviceRequest("android", token, "1.4.0")),
        DeviceResponse.class);
  }

  private NotificationListResponse inbox(RegisteredTestUser user, String query) {
    return restTemplate
        .exchange(
            baseUrl("/me/notifications" + query),
            HttpMethod.GET,
            new HttpEntity<>(authHeaders(login(user).accessToken())),
            NotificationListResponse.class)
        .getBody();
  }
}
