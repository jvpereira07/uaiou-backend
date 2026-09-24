-- Modo feira (docs/feira/): bônus por constância. Além do prêmio de cada pedido, o operador cadastra
-- metas do tipo "complete N entregas e ganhe X". Quem atinge a meta conquista o bônus uma vez, recebe
-- notificação no app e retira o prêmio no balcão, como os demais.

create table feira_bonus (
    id           uuid primary key,
    titulo       varchar(60) not null,
    -- Quantas entregas FINALIZADAS o jogador precisa ter para conquistar.
    meta         integer     not null check (meta between 1 and 999),
    -- Texto livre, como feira_recompensa do pedido: é o prêmio físico, não dinheiro.
    recompensa   varchar(60) not null,
    criado_em    timestamptz not null default now(),
    -- Não-nulo = parou de ser conquistável; as conquistas já feitas continuam valendo.
    encerrado_em timestamptz
);

create table feira_bonus_conquista (
    id             uuid primary key,
    bonus_id       uuid        not null references feira_bonus (id),
    entregador_id  uuid        not null references entregador (usuario_id),
    conquistado_em timestamptz not null default now(),
    -- Nulo = conquistou e ainda não retirou o prêmio no balcão.
    entregue_em    timestamptz,

    -- Uma vez por pessoa por bônus. Duas finalizações simultâneas do mesmo jogador não podem
    -- conceder o mesmo bônus em dobro, e quem decide isso é o banco.
    constraint ux_feira_bonus_conquista unique (bonus_id, entregador_id)
);

create index ix_feira_bonus_conquista_entregador on feira_bonus_conquista (entregador_id);
