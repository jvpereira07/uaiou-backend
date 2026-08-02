package com.uaiou.shared.config;

import java.util.List;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Falha rápido, com mensagem clara, quando falta uma variável de ambiente obrigatória — em vez de
 * deixar o Spring tentar montar o {@code DataSource} e estourar uma exceção genérica de placeholder
 * não resolvido (RF-01.3, critério de aceite 3).
 *
 * <p>Roda no evento {@link ApplicationEnvironmentPreparedEvent}, disparado antes de o contexto
 * Spring existir — ou seja, antes de qualquer tentativa de conexão com o banco. Registrado via
 * {@code META-INF/spring.factories} (não é um {@code @Bean}: nesse ponto do boot ainda não há bean
 * factory).
 *
 * <p>Desativado no perfil {@code test}: os testes de integração usam Testcontainers, que injeta as
 * propriedades de conexão dinamicamente — as variáveis de ambiente literais não existem nesse
 * cenário, e não deveriam.
 */
public class EnvironmentValidator
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

  private static final List<String> REQUIRED_VARIABLES =
      List.of("DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD");

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    validate(event.getEnvironment());
  }

  /**
   * Lógica pura, sem depender da assinatura do evento do Spring Boot — é o que {@code
   * EnvironmentValidatorTest} exercita.
   */
  void validate(Environment environment) {
    if (isTestProfileActive(environment)) {
      return;
    }

    List<String> missing =
        REQUIRED_VARIABLES.stream()
            .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
            .toList();

    if (!missing.isEmpty()) {
      throw new IllegalStateException(
          "Configuração ausente: defina a(s) variável(is) de ambiente "
              + String.join(", ", missing)
              + ". Veja backend/.env.example para o conjunto completo de chaves esperadas.");
    }
  }

  private boolean isTestProfileActive(Environment environment) {
    return List.of(environment.getActiveProfiles()).contains("test");
  }
}
