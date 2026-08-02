-- Um push_token nunca pode apontar para dois usuários — reinstalação/troca de conta faz UPSERT pelo
-- token, não INSERT duplicado (RF-08.3), daí a UNIQUE simples (não composta com usuario_id).
create table dispositivo (
    id             uuid primary key,
    usuario_id     uuid        not null references usuario (id),
    plataforma     varchar(20) not null,
    push_token     varchar(255) not null,
    app_version    varchar(20),
    ultimo_uso_em  timestamptz not null default now(),
    criado_em      timestamptz not null default now(),

    constraint uk_dispositivo_push_token unique (push_token),
    constraint ck_dispositivo_plataforma check (plataforma in ('android', 'ios', 'web'))
);

create index ix_dispositivo_usuario on dispositivo (usuario_id);

-- tipo é catálogo aberto (14+ valores em notificacoes.md, tendendo a crescer) — não constrangido por CHECK,
-- mesmo raciocínio de registro_auditoria.acao. prioridade é um conjunto fechado e pequeno, vale constranger:
-- eventos "urgente" (contingência de código) atravessam o modo soneca do provedor de push (RF-08.8).
create table notificacao (
    id           uuid primary key,
    usuario_id   uuid        not null references usuario (id),
    tipo         varchar(60) not null,
    prioridade   varchar(20) not null default 'normal',
    titulo       varchar(160) not null,
    corpo        text,
    payload      jsonb,
    lida_em      timestamptz,
    criado_em    timestamptz not null default now(),

    constraint ck_notificacao_prioridade check (prioridade in ('normal', 'urgente'))
);

create index ix_notificacao_usuario_nao_lida on notificacao (usuario_id, lida_em);
