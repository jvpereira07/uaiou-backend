package com.uaiou.users.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.users.dto.MeResponse;
import com.uaiou.users.dto.PatchMeRequest;
import com.uaiou.users.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/usuarios.md} cobertos por T-04. */
@RestController
@RequestMapping("/me")
public class MeController {

  private final ProfileService profileService;
  private final CurrentUserHolder currentUserHolder;

  public MeController(ProfileService profileService, CurrentUserHolder currentUserHolder) {
    this.profileService = profileService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public MeResponse getMe() {
    AuthenticatedUser user = currentUserHolder.require();
    return profileService.getMe(user.userId());
  }

  @PatchMapping
  public MeResponse patchMe(@Valid @RequestBody PatchMeRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    return profileService.patchMe(user.userId(), request);
  }

  @DeleteMapping("/sessions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeAllSessions() {
    AuthenticatedUser user = currentUserHolder.require();
    profileService.revokeAllSessions(user.userId());
  }
}
