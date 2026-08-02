package com.uaiou.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Conflito de estado — ex.: pedido já atribuído, recurso duplicado. HTTP 409. */
public class ConflictException extends ApiException {

  public ConflictException(String code, String message) {
    super(code, message);
  }

  public ConflictException(String code, String message, Map<String, Object> details) {
    super(code, message, null, details);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.CONFLICT;
  }
}
