-- Gestão de entregas pelo painel admin: intervenção manual no estado do pedido e timeouts
-- configuráveis.
--
-- O admin pode cancelar um pedido já coletado (pacote extraviado, fraude): o marco da coleta fica,
-- é histórico — então "cancelado" passa a conviver com coletado_em.
alter table pedido drop constraint ck_pedido_coleta_coerente;
alter table pedido add constraint ck_pedido_coleta_coerente check (
    coletado_em is null or status in ('coletado', 'finalizado', 'finalizado_contestavel', 'cancelado')
);

-- Uma linha por regra de timeout. Nasce DESLIGADA: ligar um job que cancela pedidos reais é decisão
-- do operador, não efeito colateral de subir a versão.
create table configuracao_timeout (
    chave            varchar(40)  primary key,
    duracao_minutos  integer      not null,
    ativo            boolean      not null default false,
    atualizado_em    timestamptz  not null default now(),
    atualizado_por   uuid         references admin (usuario_id),

    constraint ck_configuracao_timeout_chave check (chave in (
        'publicado_sem_aceite', 'aceito_sem_coleta', 'coletado_sem_finalizacao'
    )),
    constraint ck_configuracao_timeout_duracao check (duracao_minutos between 1 and 43200)
);

insert into configuracao_timeout (chave, duracao_minutos) values
    ('publicado_sem_aceite', 120),
    ('aceito_sem_coleta', 60),
    ('coletado_sem_finalizacao', 180);

-- Registro de cada disparo: o job não tem admin para assinar registro_auditoria, e sem isto um
-- pedido que "voltou sozinho" para a vitrine seria inexplicável.
create table ocorrencia_timeout (
    id               uuid         primary key,
    pedido_id        uuid         not null references pedido (id),
    chave            varchar(40)  not null,
    acao             varchar(30)  not null,
    status_anterior  varchar(30)  not null,
    criado_em        timestamptz  not null default now(),

    constraint ck_ocorrencia_timeout_acao check (acao in ('cancelado', 'devolvido_vitrine', 'sinalizado'))
);

create index ix_ocorrencia_timeout_pedido on ocorrencia_timeout (pedido_id, chave);
create index ix_ocorrencia_timeout_criado on ocorrencia_timeout (criado_em desc);
create index ix_pedido_status_criado on pedido (status, criado_em desc);
