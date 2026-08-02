# uaiou-backend

API do **UaiOu** — marketplace que conecta estabelecimentos a entregadores para redirecionamento de entregas.

Este repositório é só o backend (Spring Boot). Documentação de negócio, casos de uso, modelo de domínio, esquema de dados e contrato de API completo vivem no vault de documentação do projeto (`system-documentation/` e `docs/`, em repositório à parte).

## Stack

Java 25 · Spring Boot 4.1 · PostgreSQL 16 · Flyway · MinIO · Redis · Maven. Decisão registrada em [docs/decisions/0001-stack.md](docs/decisions/0001-stack.md).

## Subir o ambiente

### Opção rápida — tudo em Docker

```bash
cp .env.example .env
docker compose up --build
```

A API sobe em `http://localhost:8080/api/v1`, com PostgreSQL, MinIO (console em `http://localhost:9001`) e Redis já configurados.

Verifique:

```bash
curl http://localhost:8080/api/v1/health
```

### Opção de desenvolvimento — dependências em Docker, app local

Útil para hot reload / debug pela IDE.

```bash
cp .env.example .env
docker compose up postgres minio redis
```

Depois, com as variáveis do `.env` exportadas no shell (ou configuradas na sua IDE):

```bash
./mvnw spring-boot:run
```

## Rodar os testes

```bash
./mvnw test      # unitários — rápidos, sem Docker
./mvnw verify     # unitários + integração (Testcontainers — precisa do Docker rodando)
```

Os testes de integração sobem um PostgreSQL real via Testcontainers (nunca H2 — ver ADR). Docker Desktop (ou equivalente) precisa estar de fato rodando, não só instalado — `docker info` deve responder.

## Formatação e lint

```bash
./mvnw spotless:check   # verifica
./mvnw spotless:apply   # corrige automaticamente
```

## Documentação

- [Escopo da v1](../system-documentation/task/escopo-v1.md) — o que entra e o que fica de fora nesta primeira versão
- [Backlog de tasks](../system-documentation/task/README.md)
- [Contrato da API](../system-documentation/api/README.md)
- [Esquema de dados](../system-documentation/esquema-de-dados.md)
- [Documentação de negócio (casos de uso)](../docs/README.md)
- [Decisões técnicas do backend](docs/decisions/)
