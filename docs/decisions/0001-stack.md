# 0001 — Stack do backend

**Status:** aceita
**Data:** 2026-08-01

## Contexto

Projeto greenfield (T-01 — [system-documentation/task/T-01-fundacao-do-projeto.md](../../../system-documentation/task/T-01-fundacao-do-projeto.md) no vault de documentação). A stack foi definida pelo dono do produto antes desta task: Spring Boot, PostgreSQL, Flyway, MinIO, Redis, arquitetura em camadas, com frontend web em Next.js cobrindo admin e estabelecimento. Esta ADR registra a decisão e as consequências descobertas ao implementá-la — não reabre a escolha.

## Decisão

| Camada | Escolha | Versão fixada |
|---|---|---|
| Linguagem / runtime | Java | 25 (LTS) |
| Framework | Spring Boot | 4.1.0 |
| Banco de dados | PostgreSQL | 16 |
| Migrations | Flyway | gerenciado pelo BOM do Spring Boot |
| Storage de arquivos | MinIO (API S3) | `minio/minio:latest` no compose |
| Cache | Redis | 7 |
| Arquitetura | Camadas (`controller → service → repository`) agrupadas por módulo de domínio | — |
| Build | Maven (wrapper) | 3.9.16 |
| Testes de integração | Testcontainers (PostgreSQL real — H2 proibido) | 2.0.5 (via BOM) |
| Arquitetura verificável | ArchUnit | 1.4.2 |
| Formatação | Spotless + Google Java Format | 3.9.0 |
| Logs | JSON estruturado via Logstash encoder | 8.0 |
| Frontend web | Next.js — só admin e estabelecimento | tratado em repositório próprio |

O repositório `backend/` é **independente** do repositório `app/` (Flutter, de um colega, com remoto próprio) — decisão tomada com o dono do produto ao iniciar esta task: cada stack evolui com seu próprio ciclo de CI, sem forçar toolchain Java e Flutter no mesmo pipeline.

## Alternativas consideradas

- **H2 em memória para testes** — rejeitado explicitamente. A v1 depende de comportamento transacional real do PostgreSQL (lock pessimista no aceite de pedido — T-13; transação multi-tabela na finalização — T-15). H2 daria suíte verde e produção quebrada.
- **SpotBugs/PMD para análise estática** — adiado. Dado o ineditismo da stack (Spring Boot 4 / Java 25 é combinação muito recente), preferiu-se não travar a fundação num plugin de compatibilidade incerta; a verificação estática desta fase é Spotless (formatação) + `-Xlint:all` do compilador. Revisitar quando houver mais código para justificar o investimento.
- **PostGIS para proximidade geográfica** — adiado para quando o volume de entregadores justificar; a v1 usa caixa delimitadora (lat/long com índice `btree`) + cálculo de distância na aplicação.
- **Monorepo** (`app/` + `backend/` + docs no mesmo repositório Git) — descartado nesta fase: `app/` já tinha histórico e remoto próprios; misturar exigiria reescrever ou importar esse histórico sem benefício claro agora.

## Consequências e descobertas empíricas

A stack "mais recente" trouxe rupturas reais em relação às versões que eu conhecia — documentadas aqui porque afetam diretamente como as próximas tasks devem escrever código:

1. **Spring Boot 4 usa Jackson 3, não Jackson 2.** O `ObjectMapper` real da aplicação é `tools.jackson.databind.ObjectMapper` (pacote `tools.jackson.*`), não mais `com.fasterxml.jackson.databind.*`. As **anotações** (`@JsonProperty`, `@JsonInclude` etc.) continuam em `com.fasterxml.jackson.annotation` — só `core`/`databind` migraram. Qualquer `ValueSerializer`/`ValueDeserializer`/`JacksonModule` customizado (ver `com.uaiou.shared.money`) precisa ser escrito contra a API nova.
2. **`java.time.Instant` já serializa como ISO-8601 UTC por padrão no Jackson 3**, sem módulo ou feature adicional — confirmado empiricamente. A propriedade `spring.jackson.serialization.write-dates-as-timestamps` não existe mais (o `SerializationFeature` correspondente foi removido) e **não deve** ser usada em `application.yaml`: quebra o boot.
3. **Testcontainers 2.x renomeou artefatos**: `org.testcontainers:junit-jupiter` → `org.testcontainers:testcontainers-junit-jupiter`; `org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`.
4. **`TestRestTemplate` saiu de `spring-boot-test`** e virou módulo próprio, `org.springframework.boot:spring-boot-resttestclient` (pacote `org.springframework.boot.resttestclient`). Precisa ser adicionado explicitamente como dependência de teste.
5. **Testcontainers em ambiente Windows depende do Docker Desktop estar de fato rodando** — `docker --version` responde mesmo com o daemon parado (só valida o binário cliente); a verificação real é `docker info`.
6. **`spotless-maven-plugin:2.43.0` quebra em JDK 25**: o passo `googleJavaFormat` usa uma API interna do `javac` (`com.sun.tools.javac.util.Log$DeferredDiagnosticHandler`) que mudou de assinatura no JDK 25, e falha com `NoSuchMethodError` tanto em `apply` quanto em `check`. Resolvido fixando `3.9.0` (mais recente disponível no momento desta task), que já é compatível.
7. **`archunit-junit5:1.3.0` não reconhece bytecode do Java 25** (class file major version 69): o ASM interno da versão falha silenciosamente ao importar cada classe compilada (`IllegalArgumentException: Unsupported class file major version 69`, logado como WARN, não como erro) — o que faria as regras de `LayeringArchitectureTest` "passarem vazias" pelo motivo errado (não por ausência real de classes, mas por falha de leitura). Resolvido fixando `1.4.2`; confirmado empiricamente que o warning desaparece e as classes são importadas de verdade.

## Decisões de implementação tomadas dentro deste escopo

- **Prefixo de rota único** (`/api/v1`, RF-01.4) via `server.servlet.context-path` — um único ponto de configuração, em vez de `@RequestMapping` repetido em cada controller.
- **`Money` como tipo dedicado** (`com.uaiou.shared.money`): `BigDecimal` normalizado a 2 casas com `HALF_UP`, serializado sempre como string JSON (nunca número), com `AttributeConverter` JPA (`autoApply = true`) para `numeric(12,2)`. O desserializador rejeita número JSON de propósito — não só a saída, a entrada também nunca aceita float.
- **Testcontainers em padrão *singleton container***: um único PostgreSQL para toda a suíte (bloco estático + `@DynamicPropertySource`), não `@Testcontainers`/`@Container` por classe — mantém a suíte rápida (RNF-01.2).
- **`EnvironmentValidator`** roda no evento `ApplicationEnvironmentPreparedEvent` (via `META-INF/spring.factories`, antes do contexto Spring existir) e falha com mensagem nomeando exatamente a variável ausente — desativado no perfil `test`, onde o Testcontainers injeta a conexão dinamicamente.
- **Regras de camada verificadas por ArchUnit** (`LayeringArchitectureTest`), não só documentadas: controller não depende de repository, repository não depende de DTO, controller não depende de `@Entity`, `@Transactional` não aparece em pacote `..controller..`. Passam vazias hoje (`allowEmptyShould(true)`) — passam a valer automaticamente sobre os módulos de domínio que T-03 em diante forem criando.

## Descobertas empíricas adicionais (T-02)

Com o Docker Desktop finalmente disponível nesta máquina (o bloqueio anterior era o próprio Docker Desktop travando antes de completar o boot do daemon — resolvido reiniciando o processo), a suíte de integração rodou de verdade contra PostgreSQL real e revelou mais três incompatibilidades de versão, todas silenciosas (sem erro no boot, só comportamento ausente ou sutilmente diferente):

8. **`TestRestTemplate` precisa de `@AutoConfigureTestRestTemplate` explícito.** No Boot 4, `@SpringBootTest(webEnvironment = RANDOM_PORT)` sozinho não basta mais para autoconfigurar o bean — sem a anotação, todo teste que o injeta falha com `NoSuchBeanDefinitionException`.
9. **`RestTemplateBuilder` também é módulo próprio** (`spring-boot-restclient`) — sem ele, a autoconfiguração de teste do `TestRestTemplate` nem consegue *introspectar* (falha ao carregar a classe, `NoClassDefFoundError`).
10. **`FlywayAutoConfiguration` saiu de `spring-boot-autoconfigure` e virou módulo `spring-boot-starter-flyway`.** Esta foi a mais perigosa das sete: com só `flyway-core` + `flyway-database-postgresql` no classpath (o que parecia suficiente e compilava sem erro), a aplicação **sobe normalmente e não avisa nada** — só que nenhuma migration roda, porque a autoconfiguração que chamaria o Flyway simplesmente não existe no contexto. `ddl-auto: validate` não pega isso porque não há entidades JPA ainda (T-03+). Só foi detectado porque o teste `flywayActuallyRanAgainstTheRealDatabase` (escrito de propósito para provar isso, RF-02.9/critério 3) falhou. **Lição:** o teste que verifica "a migration realmente rodou" não é redundante — é a única coisa que pega esse tipo de falha silenciosa.
11. **HTTP 422 foi renomeado**: `HttpStatus.UNPROCESSABLE_ENTITY` (nome antigo, WebDAV) continua existindo, mas `HttpStatus.valueOf(422)` no cliente resolve para `HttpStatus.UNPROCESSABLE_CONTENT` (nome atual, RFC 9110) — os dois têm o código 422, mas são instâncias de enum diferentes e `.equals()` falha entre eles. `BusinessRuleException` e os testes foram atualizados para `UNPROCESSABLE_CONTENT`.
12. **`EXPLAIN` em tabela pequena não prova uso de índice.** Com 1.000 linhas o Postgres ainda escolhe Seq Scan por ser genuinamente mais barato — não é defeito do índice, é o otimizador funcionando corretamente. `IndexUsageIntegrationTest` passou a usar `SET LOCAL enable_seqscan = off` dentro da transação do teste para provar que o índice **satisfaz** a consulta, em vez de tentar induzir o planejador a escolhê-lo por custo.

Verificação final: `./mvnw verify` — **52/52 testes verdes** (32 unitários + 20 de integração) — e `docker compose up --build` de ponta a ponta, com as 10 migrations + seed aplicadas (confirmado via `flyway_schema_history`) e dados reais no banco.
