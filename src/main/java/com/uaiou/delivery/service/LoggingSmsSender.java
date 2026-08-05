package com.uaiou.delivery.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação padrão enquanto não há credencial de provedor de SMS. Registra o que <em>seria</em>
 * enviado.
 *
 * <p>Deliberadamente SEM {@code @ConditionalOnMissingBean}: essa anotação só é avaliada em métodos
 * {@code @Bean} de autoconfiguração, não em classe varrida por component scan — usá-la aqui faria a
 * aplicação subir sem nenhum {@code SmsSender} registrado (mesmo bug encontrado empiricamente em
 * {@code LoggingPushSender}, T-08). Quando entrar um provedor real, basta anotá-lo com
 * {@code @Primary}.
 */
@Component
public class LoggingSmsSender implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

  @Override
  public void send(String phoneE164, String message) {
    log.info("SMS (simulado) para {}: {}", phoneE164, message);
  }
}
