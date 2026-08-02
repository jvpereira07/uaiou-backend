package com.uaiou.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Papel ou status do usuário sem permissão para a ação (ex.: suspenso, bloqueado). HTTP 403. */
public class ForbiddenException extends ApiException {

  public ForbiddenException(String code, String message) {
    super(code, message);
  }

  public ForbiddenException(String code, String message, Map<String, Object> details) {
    super(code, message, null, details);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.FORBIDDEN;
  }
}
