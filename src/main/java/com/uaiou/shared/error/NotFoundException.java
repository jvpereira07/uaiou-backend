package com.uaiou.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Recurso inexistente ou fora do escopo de quem pede — as duas situações respondem 404, nunca 403,
 * para não revelar a existência de um recurso alheio (ver api/README.md).
 */
public class NotFoundException extends ApiException {

  public NotFoundException(String code, String message) {
    super(code, message);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.NOT_FOUND;
  }
}
