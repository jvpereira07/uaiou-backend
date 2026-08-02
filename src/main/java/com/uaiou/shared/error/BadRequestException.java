package com.uaiou.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Payload malformado ou parâmetro inválido. HTTP 400. */
public class BadRequestException extends ApiException {

  public BadRequestException(String code, String message) {
    super(code, message);
  }

  public BadRequestException(String code, String message, Map<String, Object> details) {
    super(code, message, null, details);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.BAD_REQUEST;
  }
}
