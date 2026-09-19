-- Limites de comportamento configuráveis pelo painel admin: "N ocorrências numa janela bloqueiam
-- por um tempo". Substitui app.pickup.withdrawal-max-per24h/withdrawal-cooldown (RF-26.29), que
-- exigiam deploy para calibrar. Os valores semeados são os que vigoravam na configuração.
create table limite_comportamento (
    chave            varchar(40)  primary key,
    maximo           integer      not null,
    janela_minutos   integer      not null,
    bloqueio_minutos integer      not null,
    ativo            boolean      not null,
    atualizado_em    timestamptz  not null default now(),
    atualizado_por   uuid         references admin (usuario_id),

    constraint ck_limite_comportamento_chave check (chave in (
        'desistencia_entregador', 'cancelamento_estabelecimento'
    )),
    constraint ck_limite_comportamento_maximo check (maximo between 1 and 1000),
    constraint ck_limite_comportamento_janela check (janela_minutos between 1 and 43200),
    constraint ck_limite_comportamento_bloqueio check (bloqueio_minutos between 1 and 43200)
);

-- Desistência já existia e continua ligada; cancelamento da loja é novo e nasce desligado.
insert into limite_comportamento (chave, maximo, janela_minutos, bloqueio_minutos, ativo) values
    ('desistencia_entregador', 3, 1440, 120, true),
    ('cancelamento_estabelecimento', 5, 1440, 120, false);

create index ix_pedido_cancelamentos_estabelecimento on pedido (estabelecimento_id, cancelado_em desc)
    where status = 'cancelado';
