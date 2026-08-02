# Rollback manual das migrations

Scripts de reversão para cada migration em `src/main/resources/db/migration/` — **não** são lidos pelo Flyway (ficam fora de `src/main/resources`, de propósito). Flyway Community não tem `undo`; isto é para um operador rodar manualmente, com o banco parado ou em janela de manutenção.

Cada `R{n}__descricao.sql` é o espelho exato do `V{n}__descricao.sql` correspondente: dropa o que a migration criou, na ordem inversa de dependência.

## Uso

Para reverter até (e incluindo) uma versão `N`, execute os scripts de `R{mais_recente}` até `R{N}`, em ordem decrescente:

```bash
psql "$DATABASE_URL" -f db/rollback/R10__dispositivo_notificacao.sql
psql "$DATABASE_URL" -f db/rollback/R9__suporte.sql
# ... até o R{N} desejado
```

Depois, ajuste `flyway_schema_history` manualmente (delete as linhas das versões revertidas) antes de reaplicar `flyway migrate` com uma migration corrigida — Flyway não faz isso sozinho.

## Regra

Migration é **imutável após merge** (RNF-02.1): correção de um erro vira migration **nova**, nunca edição da antiga. O rollback existe para o caso raro de precisar desfazer em produção, não como parte do fluxo normal de correção.
