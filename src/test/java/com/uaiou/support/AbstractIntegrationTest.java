package com.uaiou.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base para testes de integração (RF-01.13): sobe um PostgreSQL real via Testcontainers.
 *
 * <p>Usa o padrão "singleton container" — um único container Postgres, iniciado uma vez em bloco
 * estático e nunca parado explicitamente (o reaper do Testcontainers cuida disso ao fim da JVM) —
 * em vez de {@code @Testcontainers}/{@code @Container}, que reiniciaria um container por classe de
 * teste. Com isso, o Spring também reutiliza o mesmo {@code ApplicationContext} entre as subclasses
 * (mesma assinatura de configuração), o que mantém a suíte de integração rápida (RNF-01.2).
 *
 * <p>Mesmo tag de imagem do docker-compose de desenvolvimento ({@code postgres:16-alpine}) —
 * comportamento idêntico entre "funciona no meu ambiente" e "funciona no CI".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @LocalServerPort protected int port;

  @Autowired protected TestRestTemplate restTemplate;

  protected String baseUrl(String path) {
    return "http://localhost:" + port + "/api/v1" + path;
  }
}
