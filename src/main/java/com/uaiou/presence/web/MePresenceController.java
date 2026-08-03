package com.uaiou.presence.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.presence.dto.AvailabilityResponse;
import com.uaiou.presence.dto.UpdateAvailabilityRequest;
import com.uaiou.presence.dto.UpdateLocationRequest;
import com.uaiou.presence.service.CourierPresenceService;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/usuarios.md} cobertos por T-10. */
@RestController
@RequestMapping("/me")
public class MePresenceController {

  private final CourierPresenceService courierPresenceService;
  private final CurrentUserHolder currentUserHolder;

  public MePresenceController(
      CourierPresenceService courierPresenceService, CurrentUserHolder currentUserHolder) {
    this.courierPresenceService = courierPresenceService;
    this.currentUserHolder = currentUserHolder;
  }

  @PutMapping("/availability")
  public AvailabilityResponse setAvailability(
      @Valid @RequestBody UpdateAvailabilityRequest request) {
    AuthenticatedUser user = requireCourier();
    return courierPresenceService.setAvailability(user.userId(), request.available());
  }

  /** RF-10.4 — 204: sem corpo de resposta, nada a ler de volta na rota de maior volume. */
  @PutMapping("/location")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateLocation(@Valid @RequestBody UpdateLocationRequest request) {
    AuthenticatedUser user = requireCourier();
    courierPresenceService.updateLocation(user.userId(), request);
  }

  private AuthenticatedUser requireCourier() {
    AuthenticatedUser user = currentUserHolder.require();
    if (user.role() != Role.COURIER) {
      throw new ForbiddenException("COURIER_ONLY", "Rota restrita a entregadores.");
    }
    return user;
  }
}
