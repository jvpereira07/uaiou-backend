package com.uaiou.shared.error;

import org.springframework.http.HttpStatus;

/** Papel ou status do usuário sem permissão para a ação (ex.: suspenso, bloqueado). HTTP 403. */
public class ForbiddenException extends ApiException {

  public ForbiddenException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.FORBIDDEN;
  }
}
