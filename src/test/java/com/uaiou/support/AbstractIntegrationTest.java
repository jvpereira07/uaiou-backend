package com.uaiou.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Base para testes de integração (RF-01.13): sobe um PostgreSQL e um MinIO reais via
 * Testcontainers.
 *
 * <p>Usa o padrão "singleton container" — os containers sobem uma vez em bloco estático e nunca
 * param explicitamente (o reaper do Testcontainers cuida disso ao fim da JVM) — em vez de
 * {@code @Testcontainers}/{@code @Container}, que reiniciaria um container por classe de teste. Com
 * isso, o Spring também reutiliza o mesmo {@code ApplicationContext} entre as subclasses (mesma
 * assinatura de configuração), o que mantém a suíte de integração rápida (RNF-01.2).
 *
 * <p>Mesma tag de imagem do docker-compose de desenvolvimento ({@code postgres:16-alpine} / {@code
 * minio/minio:latest}) — comportamento idêntico entre "funciona no meu ambiente" e "funciona no
 * CI".
 *
 * <p>O MinIO sobe para TODOS os testes de integração, não só os de uploads (T-05): o inicializador
 * de bucket ({@code MinioBucketInitializer}) roda como {@code ApplicationRunner} em qualquer
 * contexto Spring que suba, então precisa de um MinIO de verdade para não quebrar testes que nem
 * tocam em upload. Não há distinção "internal"/"public" aqui — o processo de teste alcança o
 * container do mesmo jeito de qualquer forma, ao contrário do backend rodando dentro do
 * docker-compose.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  protected static final String MINIO_ACCESS_KEY = "uaiou-teste";
  protected static final String MINIO_SECRET_KEY = "senha-de-teste-minio";
  protected static final String MINIO_BUCKET = "uaiou-uploads-teste";

  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  private static final GenericContainer<?> MINIO =
      new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
          .withCommand("server", "/data")
          .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
          .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
          .withExposedPorts(9000)
          .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

  static {
    POSTGRES.start();
    MINIO.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);

    // app.jwt.secret e app.google.client-id (T-03) não têm valor padrão em application.yaml de
    // propósito —
    // são segredo/config real, não algo que devesse ter um fallback silencioso em produção. Nos
    // testes,
    // fixados aqui pelo mesmo motivo que o datasource: "mvn verify" precisa funcionar sem depender
    // de
    // variável de ambiente exportada manualmente antes.
    registry.add(
        "app.jwt.secret", () -> "segredo-de-integracao-usado-só-em-teste-nunca-em-producao");
    registry.add("app.google.client-id", () -> "client-id-de-integracao-usado-só-em-teste");

    String minioEndpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    registry.add("app.minio.internal-endpoint", () -> minioEndpoint);
    registry.add("app.minio.public-endpoint", () -> minioEndpoint);
    registry.add("app.minio.access-key", () -> MINIO_ACCESS_KEY);
    registry.add("app.minio.secret-key", () -> MINIO_SECRET_KEY);
    registry.add("app.minio.bucket", () -> MINIO_BUCKET);
  }

  @LocalServerPort protected int port;

  @Autowired protected TestRestTemplate restTemplate;

  protected String baseUrl(String path) {
    return "http://localhost:" + port + "/api/v1" + path;
  }
}
