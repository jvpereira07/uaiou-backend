-- refresh_token: hash persistido, nunca o token em claro. familia_id permite revogar toda a família de uma
-- vez quando um refresh já consumido é reapresentado (sinal de vazamento — RF-03.8).
create table refresh_token (
    id            uuid primary key,
    usuario_id    uuid        not null references usuario (id),
    token_hash    varchar(255) not null,
    familia_id    uuid        not null,
    expira_em     timestamptz not null,
    revogado_em   timestamptz,
    criado_em     timestamptz not null default now(),

    constraint uk_refresh_token_hash unique (token_hash)
);

create index ix_refresh_token_usuario on refresh_token (usuario_id);
create index ix_refresh_token_familia on refresh_token (familia_id);

-- Documentos enviados para aprovação de cadastro (RN-11.1: usuário só opera depois de aprovado).
create table documento_cadastro (
    id                uuid primary key,
    usuario_id        uuid        not null references usuario (id),
    tipo              varchar(30) not null,
    upload_id         uuid        not null references upload (id),
    status_aprovacao  varchar(20) not null default 'pendente',
    motivo_rejeicao   text,
    avaliado_por      uuid references admin (usuario_id),
    avaliado_em       timestamptz,
    criado_em         timestamptz not null default now(),
    atualizado_em     timestamptz not null default now(),

    constraint ck_documento_cadastro_tipo check (
        tipo in ('documento_identidade', 'cnh', 'documento_veiculo', 'documento_cnpj')
    ),
    constraint ck_documento_cadastro_status check (
        status_aprovacao in ('pendente', 'aprovado', 'rejeitado')
    )
);

create index ix_documento_cadastro_usuario on documento_cadastro (usuario_id, status_aprovacao);

-- Suspensão (com prazo) ou banimento (sem prazo) — RN-11.3: não interrompe entrega em andamento, isso é
-- responsabilidade da aplicação, não do schema.
create table sancao (
    id                uuid primary key,
    usuario_alvo_id   uuid        not null references usuario (id),
    admin_id          uuid        not null references admin (usuario_id),
    tipo              varchar(20) not null,
    motivo            text        not null,
    inicio            timestamptz not null default now(),
    fim               timestamptz,
    ativa             boolean     not null default true,
    criado_em         timestamptz not null default now(),
    atualizado_em     timestamptz not null default now(),

    constraint ck_sancao_tipo check (tipo in ('suspensao', 'banimento')),
    -- banimento não tem prazo; suspensão precisa de um, senão vira banimento por engano.
    constraint ck_sancao_fim_coerente check (
        (tipo = 'banimento' and fim is null) or (tipo = 'suspensao' and fim is not null)
    )
);

create index ix_sancao_usuario_ativa on sancao (usuario_alvo_id) where ativa;

-- Append-only por desenho (RF-02.3): sem atualizado_em, e a aplicação nunca expõe update/delete (T-07).
-- referencia_tipo é polimórfica de propósito (aponta pedido, transacao_credito, lancamento_frete, sancao
-- etc.) — checá-la aqui exigiria migration nova a cada tipo de referência novo, sem ganho real de
-- segurança (a aplicação já decide o que é uma referência válida).
create table registro_auditoria (
    id               uuid primary key,
    admin_id         uuid        not null references admin (usuario_id),
    acao             varchar(80) not null,
    referencia_tipo  varchar(40),
    referencia_id    uuid,
    motivo           text        not null,
    valor            numeric(12, 2),
    criado_em        timestamptz not null default now()
);

create index ix_registro_auditoria_admin on registro_auditoria (admin_id, criado_em desc);
create index ix_registro_auditoria_referencia on registro_auditoria (referencia_tipo, referencia_id);
