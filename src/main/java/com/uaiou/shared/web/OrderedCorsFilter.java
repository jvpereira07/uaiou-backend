package com.uaiou.shared.web;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Responde à negociação de CORS (RF-A13.1) — o que destrava o app Flutter rodando em navegador.
 *
 * <p>Precisa rodar <strong>antes de {@link com.uaiou.auth.web.JwtAuthenticationFilter}</strong>: o
 * preflight é um {@code OPTIONS} sem header {@code Authorization}, e é este filtro que o responde e
 * encerra a cadeia. Fica depois de {@link CorrelationIdFilter} de propósito, para que o preflight
 * também apareça nos logs com identificador de correlação.
 *
 * <p>Subclasse só para anotar {@code @Order} na classe, no mesmo padrão de {@link
 * OrderedRequestContextFilter}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class OrderedCorsFilter extends CorsFilter {

  /**
   * Métodos do contrato. {@code OPTIONS} não entra: o preflight é respondido por este filtro, não
   * roteado para controller.
   */
  private static final List<String> METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

  /**
   * {@code Idempotency-Key} é exigido pelo contrato em atribuição e finalização; sem ele aqui, o
   * navegador barra justamente as requisições que não podem ser repetidas por engano.
   */
  private static final List<String> ALLOWED_HEADERS =
      List.of(
          HttpHeaders.AUTHORIZATION,
          HttpHeaders.CONTENT_TYPE,
          HttpHeaders.ACCEPT,
          "Idempotency-Key",
          CorrelationIdFilter.HEADER_NAME);

  /** O cliente precisa ler o id da requisição para referenciá-lo num chamado de suporte. */
  private static final List<String> EXPOSED_HEADERS = List.of(CorrelationIdFilter.HEADER_NAME);

  private static final long PREFLIGHT_CACHE_SECONDS = 1800;

  public OrderedCorsFilter(CorsProperties properties) {
    super(source(properties));
  }

  private static UrlBasedCorsConfigurationSource source(CorsProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(METHODS);
    configuration.setAllowedHeaders(ALLOWED_HEADERS);
    configuration.setExposedHeaders(EXPOSED_HEADERS);
    configuration.setMaxAge(PREFLIGHT_CACHE_SECONDS);

    // Desligado porque a sessão é Bearer token em header, não cookie: o navegador não precisa
    // mandar credencial de origem cruzada. Ligar isto proibiria `*` e ampliaria a superfície sem
    // dar nada em troca.
    configuration.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
