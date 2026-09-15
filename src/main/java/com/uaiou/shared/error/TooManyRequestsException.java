package com.uaiou.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Limite de frequência de uma ação do usuário — ex.: reaviso de coleta (RF-26.10). HTTP 429. */
public class TooManyRequestsException extends ApiException {

  public TooManyRequestsException(String code, String message, Map<String, Object> details) {
    super(code, message, null, details);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.TOO_MANY_REQUESTS;
  }
}
