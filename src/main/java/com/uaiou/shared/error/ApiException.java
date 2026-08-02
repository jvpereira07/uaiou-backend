package com.uaiou.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base de toda exceção que vira resposta HTTP no envelope de erro do contrato ({@link
 * ErrorResponse}). Cada subtipo fixa o {@link HttpStatus} correspondente à sua categoria (ver
 * api/README.md — mapeamento padrão).
 */
public abstract class ApiException extends RuntimeException {

  private final String code;
  private final String rule;
  private final Map<String, Object> details;

  protected ApiException(String code, String message, String rule, Map<String, Object> details) {
    super(message);
    this.code = code;
    this.rule = rule;
    this.details = details;
  }

  protected ApiException(String code, String message) {
    this(code, message, null, null);
  }

  public abstract HttpStatus httpStatus();

  public String code() {
    return code;
  }

  /**
   * Código da regra de negócio de origem (ex.: "RN-05.1"). Nulo quando o erro não decorre de regra
   * de negócio.
   */
  public String rule() {
    return rule;
  }

  public Map<String, Object> details() {
    return details;
  }
}
