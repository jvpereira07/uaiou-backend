-- referencia_tipo/referencia_id são polimórficas de propósito (mesma razão de registro_auditoria em V3):
-- apontam pedido, transacao_credito, lancamento_frete etc., sem FK física — checar isso aqui acoplaria o
-- schema a cada novo tipo de referência que o suporte passar a aceitar.
create table chamado_suporte (
    id               uuid primary key,
    autor_id         uuid        not null references usuario (id),
    admin_id         uuid references admin (usuario_id),
    assunto          varchar(160) not null,
    status           varchar(20) not null default 'aberto',
    referencia_tipo  varchar(40),
    referencia_id    uuid,
    resolvido_em     timestamptz,
    criado_em        timestamptz not null default now(),
    atualizado_em    timestamptz not null default now(),

    constraint ck_chamado_suporte_status check (status in ('aberto', 'em_atendimento', 'resolvido'))
);

create index ix_chamado_suporte_autor on chamado_suporte (autor_id, criado_em desc);
create index ix_chamado_suporte_fila on chamado_suporte (status, criado_em);

-- Thread do chamado — append-only, sem atualizado_em.
create table chamado_mensagem (
    id           uuid primary key,
    chamado_id   uuid not null references chamado_suporte (id),
    autor_id     uuid not null references usuario (id),
    mensagem     text not null,
    criado_em    timestamptz not null default now()
);

create index ix_chamado_mensagem_chamado on chamado_mensagem (chamado_id, criado_em);
