package com.uaiou.notifications.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.notifications.dto.DeviceResponse;
import com.uaiou.notifications.dto.NotificationListResponse;
import com.uaiou.notifications.dto.NotificationPreferencesResponse;
import com.uaiou.notifications.dto.NotificationSummary;
import com.uaiou.notifications.dto.RegisterDeviceRequest;
import com.uaiou.notifications.dto.UpdateNotificationPreferencesRequest;
import com.uaiou.notifications.service.DeviceService;
import com.uaiou.notifications.service.NotificationInboxService;
import com.uaiou.notifications.service.NotificationPreferenceService;
import com.uaiou.shared.pagination.PagingRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de {@code system-documentation/api/notificacoes.md} (T-08). Todos servem a QUALQUER
 * papel — notificação não é privilégio de entregador ou estabelecimento.
 */
@RestController
@RequestMapping("/me")
public class MeNotificationsController {

  private final NotificationInboxService inboxService;
  private final DeviceService deviceService;
  private final NotificationPreferenceService preferenceService;
  private final CurrentUserHolder currentUserHolder;

  public MeNotificationsController(
      NotificationInboxService inboxService,
      DeviceService deviceService,
      NotificationPreferenceService preferenceService,
      CurrentUserHolder currentUserHolder) {
    this.inboxService = inboxService;
    this.deviceService = deviceService;
    this.preferenceService = preferenceService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping("/notifications")
  public NotificationListResponse notifications(
      @RequestParam(required = false) Boolean unread,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer perPage) {
    return inboxService.list(me().userId(), unread, type, PagingRequest.of(page, perPage));
  }

  @PutMapping("/notifications/{id}/read")
  public NotificationSummary markRead(@PathVariable UUID id) {
    return inboxService.markRead(me().userId(), id);
  }

  @PutMapping("/notifications/read")
  public Map<String, Long> markAllRead() {
    return Map.of("marked", inboxService.markAllRead(me().userId()));
  }

  @PostMapping("/devices")
  @ResponseStatus(HttpStatus.CREATED)
  public DeviceResponse registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
    return deviceService.register(me().userId(), request);
  }

  @GetMapping("/devices")
  public List<DeviceResponse> devices() {
    return deviceService.list(me().userId());
  }

  @DeleteMapping("/devices/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeDevice(@PathVariable UUID id) {
    deviceService.remove(me().userId(), id);
  }

  @GetMapping("/notification-preferences")
  public NotificationPreferencesResponse preferences() {
    return preferenceService.get(me().userId());
  }

  @PutMapping("/notification-preferences")
  public NotificationPreferencesResponse updatePreferences(
      @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
    return preferenceService.update(me().userId(), request);
  }

  private AuthenticatedUser me() {
    return currentUserHolder.require();
  }
}
