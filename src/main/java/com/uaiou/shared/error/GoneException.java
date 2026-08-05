package com.uaiou.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Recurso existiu mas não está mais acessível (ex.: código de entrega após a finalização). HTTP
 * 410.
 */
public class GoneException extends ApiException {

  public GoneException(String code, String message) {
    super(code, message);
  }

  public GoneException(String code, String message, Map<String, Object> details) {
    super(code, message, null, details);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.GONE;
  }
}
