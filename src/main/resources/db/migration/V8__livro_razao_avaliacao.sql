-- Livro-razão da v1 (escopo-v1.md): um lançamento por entrega, lido pelos dois lados — "a receber" para o
-- entregador, "a pagar" para o estabelecimento. A v1 registra o valor devido; não o movimenta (sem saldo,
-- sem débito automático). status = 'acertado' é setado pelo ENTREGADOR confirmando o recebimento fora da
-- plataforma — decisão registrada no escopo-v1, não pelo estabelecimento.
create table lancamento_frete (
    id                  uuid primary key,
    pedido_id           uuid           not null references pedido (id),
    entregador_id       uuid           not null references entregador (usuario_id),
    estabelecimento_id  uuid           not null references estabelecimento (usuario_id),
    valor               numeric(12, 2) not null,
    status              varchar(20)    not null default 'a_receber',
    acertado_em         timestamptz,
    acertado_por        uuid references usuario (id),
    criado_em           timestamptz    not null default now(),
    atualizado_em       timestamptz    not null default now(),

    constraint uk_lancamento_frete_pedido unique (pedido_id),
    constraint ck_lancamento_frete_status check (status in ('a_receber', 'acertado')),
    constraint ck_lancamento_frete_valor check (valor > 0),
    constraint ck_lancamento_frete_acerto_coerente check (
        (status = 'a_receber' and acertado_em is null and acertado_por is null)
        or (status = 'acertado' and acertado_em is not null and acertado_por is not null)
    )
);

create index ix_lancamento_frete_entregador on lancamento_frete (entregador_id, status);
create index ix_lancamento_frete_estabelecimento on lancamento_frete (estabelecimento_id, status);

-- Avaliação mútua pós-entrega (RN-03.1). Escala 1..5 adotada pela v1 enquanto o TODO(dono) da escala segue
-- em aberto (ver T-19) — registrado aqui, não decidido silenciosamente.
create table avaliacao (
    id          uuid primary key,
    pedido_id   uuid        not null references pedido (id),
    autor_id    uuid        not null references usuario (id),
    alvo_id     uuid        not null references usuario (id),
    nota        smallint    not null,
    comentario  text,
    ativa       boolean     not null default true,
    criado_em   timestamptz not null default now(),

    constraint uk_avaliacao_pedido_autor unique (pedido_id, autor_id),
    constraint ck_avaliacao_nota check (nota between 1 and 5),
    constraint ck_avaliacao_autor_diferente_do_alvo check (autor_id <> alvo_id)
);

create index ix_avaliacao_alvo on avaliacao (alvo_id, ativa);
