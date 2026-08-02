package com.uaiou.shared.error;

import org.springframework.http.HttpStatus;

/** Sem token, token inválido ou expirado. HTTP 401. */
public class UnauthorizedException extends ApiException {

  public UnauthorizedException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.UNAUTHORIZED;
  }
}
