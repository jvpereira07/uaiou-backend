package com.uaiou.shared.web;

import static net.logstash.logback.argument.StructuredArguments.value;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Loga um evento por requisição com método, caminho, status e duração — nunca corpo, cabeçalho ou
 * query string (RF-01.9). Essa é a garantia estrutural de que dado sensível (senha, token, código
 * de entrega) nunca aparece em log de acesso: o filtro simplesmente não tem acesso a esse conteúdo,
 * por desenho, não por uma lista de campos mascarados que poderia ficar desatualizada.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger("com.uaiou.http.access");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long startNanos = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
      log.info(
          "{} {} -> {} ({} ms)",
          value("httpMethod", request.getMethod()),
          value("path", request.getRequestURI()),
          value("status", response.getStatus()),
          value("durationMs", durationMs));
    }
  }
}
