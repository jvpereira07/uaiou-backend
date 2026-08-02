-- Código de entrega: 1:1 com o pedido. codigo_hash é usado para comparação na finalização; codigo_cifrado
-- permite a leitura pelo estabelecimento (RN-08.3) — a chave de cifra vive fora do banco (variável de
-- ambiente, T-15), então comprometer o banco sozinho não entrega os códigos ativos. leituras/ultima_leitura_em
-- são a auditoria exigida pela própria RN-08.3 (a leitura do estabelecimento é registrada).
create table otp (
    id                  uuid primary key,
    pedido_id           uuid        not null references pedido (id),
    codigo_hash         varchar(255) not null,
    codigo_cifrado      varchar(255) not null,
    tentativas          integer     not null default 0,
    status              varchar(20) not null default 'gerado',
    expira_em           timestamptz not null,
    validado_em         timestamptz,
    leituras            integer     not null default 0,
    ultima_leitura_em   timestamptz,
    criado_em           timestamptz not null default now(),
    atualizado_em       timestamptz not null default now(),

    constraint uk_otp_pedido unique (pedido_id),
    constraint ck_otp_status check (status in ('gerado', 'validado', 'expirado', 'bloqueado')),
    constraint ck_otp_tentativas check (tentativas >= 0),
    constraint ck_otp_leituras check (leituras >= 0)
);

-- Evidência da finalização: 1:1 com o pedido. RN-10.2 — foto obrigatória no modo contestável, opcional
-- (ausente) no modo por código, onde o geofence + o próprio código já são a prova.
create table evidencia_entrega (
    id                 uuid primary key,
    pedido_id          uuid          not null references pedido (id),
    tipo_finalizacao   varchar(20)   not null,
    upload_id          uuid references upload (id),
    lat                numeric(9, 6) not null,
    long               numeric(9, 6) not null,
    registrado_em      timestamptz   not null default now(),

    constraint uk_evidencia_entrega_pedido unique (pedido_id),
    constraint ck_evidencia_entrega_tipo check (tipo_finalizacao in ('otp', 'contestavel')),
    constraint ck_evidencia_entrega_upload_obrigatorio_se_contestavel check (
        tipo_finalizacao <> 'contestavel' or upload_id is not null
    )
);

-- Histórico da escada de contingência (uc-fallback-otp.md) — uma linha por evento, nunca atualizada. É a
-- base probatória usada para atribuir a falha ao estabelecimento (RN-09.3) e para o dossiê de suporte.
create table contingencia_otp (
    id          uuid primary key,
    pedido_id   uuid        not null references pedido (id),
    degrau      smallint    not null,
    canal       varchar(20) not null,
    resultado   varchar(30) not null,
    prazo_em    timestamptz,
    criado_em   timestamptz not null default now(),

    constraint ck_contingencia_otp_degrau check (degrau in (1, 2)),
    constraint ck_contingencia_otp_canal check (canal in ('sms', 'estabelecimento')),
    constraint ck_contingencia_otp_resultado check (resultado in (
        'reenviado', 'notificado', 'repassado', 'sem_telefone', 'sem_resposta', 'expirado'
    ))
);

create index ix_contingencia_otp_pedido on contingencia_otp (pedido_id, criado_em);
