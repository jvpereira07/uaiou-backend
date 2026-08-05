-- RF-18.7: o passado não é reescrito — uma correção de lançamento vira registro NOVO, vinculado ao
-- original, que continua intacto. O admin corrige o insumo publicando um ajuste, nunca um UPDATE.
create table ajuste_lancamento_frete (
    id              uuid primary key,
    lancamento_id   uuid           not null references lancamento_frete (id),
    novo_valor      numeric(12, 2) not null,
    motivo          text           not null,
    referencia_tipo varchar(50)    not null,
    referencia_id   uuid           not null,
    admin_id        uuid           not null references usuario (id),
    criado_em       timestamptz    not null default now(),

    constraint ck_ajuste_lancamento_frete_valor check (novo_valor > 0)
);

create index ix_ajuste_lancamento_frete_lancamento on ajuste_lancamento_frete (lancamento_id);

-- RF-20.4/RF-20.5: score é uma FOTO — sem a marca de tempo, ninguém sabe se um evento recente já
-- entrou no número. score/score_componentes já existem desde V1; só faltava o timestamp da última
-- consolidação.
alter table estabelecimento add column score_calculado_em timestamptz;
alter table entregador add column score_calculado_em timestamptz;
