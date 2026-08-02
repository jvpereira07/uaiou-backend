package com.uaiou.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Regra de negócio violada (ex.: RN-05.1 — créditos insuficientes). HTTP 422.
 *
 * <p>É o único tipo de exceção que sempre carrega {@code rule}: é o elo entre a falha observada em
 * produção e a regra documentada em docs/casos-de-uso.
 */
public class BusinessRuleException extends ApiException {

  public BusinessRuleException(String code, String message, String rule) {
    super(code, message, rule, null);
  }

  public BusinessRuleException(
      String code, String message, String rule, Map<String, Object> details) {
    super(code, message, rule, details);
  }

  @Override
  public HttpStatus httpStatus() {
    return HttpStatus.UNPROCESSABLE_ENTITY;
  }
}
