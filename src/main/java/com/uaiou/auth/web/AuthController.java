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
import com.uaiou.feira.config.FeiraProperties;
import com.uaiou.shared.error.ForbiddenException;
import com.uaiou.users.Role;
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
  private final FeiraProperties feiraProperties;

  public AuthController(
      RegistrationService registrationService,
      SessionService sessionService,
      PasswordResetService passwordResetService,
      CurrentUserHolder currentUserHolder,
      FeiraProperties feiraProperties) {
    this.registrationService = registrationService;
    this.sessionService = sessionService;
    this.passwordResetService = passwordResetService;
    this.currentUserHolder = currentUserHolder;
    this.feiraProperties = feiraProperties;
  }

  @PostMapping("/registrations")
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
    requireMerchantAllowed(request.role());
    return registrationService.register(request);
  }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public SessionResponse createSession(@Valid @RequestBody SessionRequest request) {
    requireMerchantAllowed(request.role());
    return sessionService.createSession(request);
  }

  /**
   * Modo feira (docs/feira/): não existe estabelecimento nesta versão — só o UaiOu fixo, semeado
   * por V28. Barrado aqui, na borda do produto, e não dentro de {@code RegistrationService}/ {@code
   * SessionService}: o feira não é uma regra do domínio de autenticação, é um recorte de
   * demonstração por cima dele. {@code role} pode ser nulo (grant "refresh" não o exige); nesse
   * caso não há o que barrar — uma conta MERCHANT não existe no banco desta demonstração para
   * renovar sessão alguma.
   */
  private void requireMerchantAllowed(Role role) {
    if (feiraProperties.enabled() && role == Role.MERCHANT) {
      throw new ForbiddenException(
          "MERCHANT_DISABLED_IN_FEIRA",
          "Esta versão de demonstração não tem conta de estabelecimento.");
    }
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
