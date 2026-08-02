-- Créditos: a única moeda controlada pela v1 (escopo-v1.md) — consumidos ao publicar pedido, atribuídos
-- pelo admin via plano. Sem dinheiro pré-pago: não existe tabela "saldo" nem "movimentacao_saldo" aqui.
create table plano (
    id                    uuid primary key,
    nome                  varchar(80)    not null,
    cota_mensal_creditos  integer        not null,
    preco                 numeric(12, 2) not null,
    ativo                 boolean        not null default true,
    criado_em             timestamptz    not null default now(),
    atualizado_em         timestamptz    not null default now(),

    constraint ck_plano_cota check (cota_mensal_creditos > 0),
    constraint ck_plano_preco check (preco >= 0)
);

create table assinatura (
    id                  uuid primary key,
    estabelecimento_id  uuid        not null references estabelecimento (usuario_id),
    plano_id            uuid        not null references plano (id),
    status              varchar(20) not null default 'ativa',
    inicio              date        not null default current_date,
    proxima_renovacao   date        not null,
    criado_em           timestamptz not null default now(),
    atualizado_em       timestamptz not null default now(),

    constraint ck_assinatura_status check (status in ('ativa', 'cancelada', 'inadimplente'))
);

-- No máximo uma assinatura ATIVA por estabelecimento — índice único parcial, não UNIQUE simples: o
-- estabelecimento pode ter histórico de assinaturas canceladas.
create unique index uk_assinatura_ativa_por_estabelecimento
    on assinatura (estabelecimento_id) where status = 'ativa';

-- Carteira de créditos: uma linha por estabelecimento (chave primária = FK, relação 1:1). O CHECK >= 0 é a
-- última linha de defesa contra saldo negativo — mesmo se a aplicação falhar em revalidar sob concorrência.
create table carteira_creditos (
    estabelecimento_id  uuid primary key references estabelecimento (usuario_id),
    saldo_creditos      integer     not null default 0,
    atualizado_em       timestamptz not null default now(),

    constraint ck_carteira_creditos_saldo_nao_negativo check (saldo_creditos >= 0)
);
