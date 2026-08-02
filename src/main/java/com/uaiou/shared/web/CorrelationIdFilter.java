package com.uaiou.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gera (ou propaga, se o cliente já enviou) um identificador de correlação por requisição. É
 * colocado no MDC para aparecer em todo log da requisição (RF-01.9) e devolvido no header de
 * resposta, para o cliente conseguir referenciar a requisição num chamado de suporte.
 *
 * <p>Roda antes de qualquer outro filtro da aplicação — o log de acesso ({@link
 * RequestLoggingFilter}) e o tratamento de erro dependem do MDC já estar populado.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String incoming = request.getHeader(HEADER_NAME);
    String requestId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();

    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER_NAME, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
