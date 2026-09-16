package com.uaiou.notifications.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * T-08 — credencial da conta de serviço do Firebase. Caminho OU conteúdo do JSON: o primeiro serve
 * ao docker-compose (arquivo montado), o segundo ao Railway (variável de ambiente).
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(String credentialsPath, String credentialsJson) {

  public boolean hasCredentials() {
    return StringUtils.hasText(credentialsPath) || StringUtils.hasText(credentialsJson);
  }
}
