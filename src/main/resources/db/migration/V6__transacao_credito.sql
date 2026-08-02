-- Extrato de créditos — o saldo em carteira_creditos é sempre derivado destes lançamentos, nunca editado
-- diretamente (princípio de registro do financeiro.md). Migration própria porque referencia pedido (V5),
-- que só existe depois de créditos/plano (V4) terem sido definidos.
create table transacao_credito (
    id                     uuid primary key,
    estabelecimento_id     uuid        not null references estabelecimento (usuario_id),
    tipo                   varchar(30) not null,
    quantidade             integer     not null,
    pedido_id              uuid references pedido (id),
    assinatura_id          uuid references assinatura (id),
    registro_auditoria_id  uuid references registro_auditoria (id),
    criado_em              timestamptz not null default now(),

    constraint ck_transacao_credito_tipo check (tipo in ('cota_mensal', 'consumo_postagem', 'ajuste')),
    -- cota mensal e ajuste positivo entram como quantidade > 0; consumo por postagem é negativo. Zero nunca
    -- é um lançamento válido — não existiria motivo para registrá-lo.
    constraint ck_transacao_credito_quantidade_nao_zero check (quantidade <> 0),
    -- consumo_postagem sempre aponta o pedido que gerou o débito; cota_mensal sempre aponta a assinatura.
    constraint ck_transacao_credito_referencia_coerente check (
        (tipo = 'consumo_postagem' and pedido_id is not null)
        or (tipo = 'cota_mensal' and assinatura_id is not null)
        or (tipo = 'ajuste')
    )
);

create index ix_transacao_credito_estabelecimento on transacao_credito (estabelecimento_id, criado_em desc);
create index ix_transacao_credito_pedido on transacao_credito (pedido_id);
