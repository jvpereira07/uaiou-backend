package com.uaiou.auth.web;

import com.uaiou.auth.dto.LogoutRequest;
import com.uaiou.auth.dto.PasswordResetConfirmation;
import com.uaiou.auth.dto.PasswordResetRequest;
import com.uaiou.auth.dto.RegisterRequest;
import com.uaiou.auth.dto.RegisterResponse;
import com.uaiou.auth.dto.SessionRequest;
import com.uaiou.auth.dto.SessionResponse;
import com.uaiou.auth.service.PasswordResetService;
import com.uaiou.auth.service.RegistrationService;
import com.uaiou.auth.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/auth.md} (T-03). */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final RegistrationService registrationService;
  private final SessionService sessionService;
  private final PasswordResetService passwordResetService;
  private final CurrentUserHolder currentUserHolder;

  public AuthController(
      RegistrationService registrationService,
      SessionService sessionService,
      PasswordResetService passwordResetService,
      CurrentUserHolder currentUserHolder) {
    this.registrationService = registrationService;
    this.sessionService = sessionService;
    this.passwordResetService = passwordResetService;
    this.currentUserHolder = currentUserHolder;
  }

  @PostMapping("/registrations")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    return registrationService.register(request);
  }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public SessionResponse createSession(@Valid @RequestBody SessionRequest request) {
    return sessionService.createSession(request);
  }

  @DeleteMapping("/sessions/current")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@RequestBody(required = false) LogoutRequest body) {
    AuthenticatedUser user = currentUserHolder.require();
    if (body != null && body.refreshToken() != null && !body.refreshToken().isBlank()) {
      sessionService.revokeCurrentSession(user.userId(), body.refreshToken());
    }
  }

  @PostMapping("/password-resets")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
    passwordResetService.requestReset(request.email());
  }

  @PutMapping("/password-resets/{token}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmPasswordReset(
      @PathVariable String token, @Valid @RequestBody PasswordResetConfirmation body) {
    passwordResetService.confirmReset(token, body.password());
  }
}
