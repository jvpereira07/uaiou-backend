package com.uaiou.shared.error;

import com.uaiou.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converte toda exceção lançada pela aplicação no envelope de erro único do contrato
 * (api/README.md). Nenhum outro ponto do código deveria montar um {@link ErrorResponse}
 * manualmente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
    if (ex.httpStatus().is5xxServerError()) {
      log.error("Erro de aplicação: {}", ex.code(), ex);
    } else {
      log.warn("Erro de requisição: {} — {}", ex.code(), ex.getMessage());
    }
    return respond(
        ex.httpStatus(), new ErrorBody(ex.code(), ex.getMessage(), ex.rule(), ex.details()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, Object> fieldErrors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
    return respond(
        HttpStatus.BAD_REQUEST,
        new ErrorBody("VALIDATION_ERROR", "Um ou mais campos são inválidos.", null, fieldErrors));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpRequestMethodNotSupportedException.class
  })
  public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
    return respond(
        HttpStatus.BAD_REQUEST,
        new ErrorBody("MALFORMED_REQUEST", "A requisição não pôde ser interpretada.", null, null));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
    return respond(
        HttpStatus.NOT_FOUND, new ErrorBody("NOT_FOUND", "Rota inexistente.", null, null));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
    log.error(
        "Erro não tratado em {} {} (requestId={})",
        request.getMethod(),
        request.getRequestURI(),
        requestId,
        ex);
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR,
        new ErrorBody(
            "INTERNAL_ERROR",
            "Erro interno. Contate o suporte informando o identificador da requisição.",
            null,
            requestId == null ? null : Map.of("requestId", requestId)));
  }

  private ResponseEntity<ErrorResponse> respond(HttpStatus status, ErrorBody body) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.CONTENT_TYPE, "application/json")
        .body(new ErrorResponse(body));
  }
}
