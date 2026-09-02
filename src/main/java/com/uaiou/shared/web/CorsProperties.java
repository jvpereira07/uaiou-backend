package com.uaiou.shared.web;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origens autorizadas a chamar a API de dentro de um navegador (RF-A13.1).
 *
 * <p>Só o cliente web sofre CORS — Android e iOS não passam por essa negociação. Mas o app Flutter
 * é executado em Chrome durante o desenvolvimento, e sem isto nenhuma tela integrada consegue falar
 * com o backend.
 *
 * <p>A lista é <strong>explícita por ambiente</strong>: nunca {@code *}. Ver {@link
 * OrderedCorsFilter} para o porquê de credenciais estarem desligadas.
 *
 * @param allowedOrigins origens completas, com esquema e porta ({@code http://localhost:5500})
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

  public CorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
