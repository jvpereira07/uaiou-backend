-- Pedido é o agregado central: atravessa todos os estados do ciclo de vida (uc-ciclo-de-vida-do-pedido.md).
-- Sem "em_disputa" na v1 — sem custódia de dinheiro, não há o que a arbitragem redistribuiria
-- (escopo-v1.md, seção 3). numero é único POR ESTABELECIMENTO, não globalmente — cada loja tem sua própria
-- numeração (🖼 "Pedido nº 0011" do protótipo).
create table pedido (
    id                      uuid primary key,
    numero                  varchar(20)    not null,
    estabelecimento_id      uuid           not null references estabelecimento (usuario_id),
    entregador_id           uuid references entregador (usuario_id),
    status                  varchar(30)    not null default 'criado',
    frete_proposto          numeric(12, 2) not null,
    frete_final             numeric(12, 2),
    creditos_consumidos     integer        not null,
    dest_bairro             varchar(80)    not null,
    dest_rua                varchar(120)   not null,
    dest_numero             varchar(10)    not null,
    dest_complemento        varchar(60),
    dest_lat                numeric(9, 6)  not null,
    dest_long               numeric(9, 6)  not null,
    recebedor_nome          varchar(120)   not null,
    recebedor_telefone      varchar(15),
    hora_prevista_entrega   timestamptz,
    aceito_em               timestamptz,
    finalizado_em           timestamptz,
    criado_em               timestamptz    not null default now(),
    atualizado_em           timestamptz    not null default now(),

    constraint uk_pedido_numero_por_estabelecimento unique (estabelecimento_id, numero),
    constraint ck_pedido_status check (status in (
        'criado', 'publicado', 'em_negociacao', 'aceito',
        'finalizado', 'finalizado_contestavel', 'cancelado'
    )),
    constraint ck_pedido_frete_proposto check (frete_proposto > 0),
    constraint ck_pedido_creditos_consumidos check (creditos_consumidos > 0),
    -- RN-01.2 (um entregador por pedido) e o vínculo do valor final nascem juntos: a partir de "aceito" os
    -- dois campos passam a ser obrigatórios; antes disso, ambos são nulos.
    constraint ck_pedido_atribuicao_coerente check (
        status not in ('aceito', 'finalizado', 'finalizado_contestavel')
        or (entregador_id is not null and frete_final is not null)
    )
);

-- Elegibilidade por proximidade: caixa delimitadora (lat/long) + status na listagem (T-11). PostGIS fica
-- para quando o volume justificar (ADR 0001, decisão registrada).
create index ix_pedido_dest_geo on pedido (dest_lat, dest_long);
create index ix_pedido_status on pedido (status);
create index ix_pedido_estabelecimento on pedido (estabelecimento_id, criado_em desc);
create index ix_pedido_entregador on pedido (entregador_id, status);

create table contraoferta (
    id               uuid primary key,
    pedido_id        uuid           not null references pedido (id),
    entregador_id    uuid           not null references entregador (usuario_id),
    valor_proposto   numeric(12, 2) not null,
    status           varchar(20)    not null default 'pendente',
    respondido_em    timestamptz,
    criado_em        timestamptz    not null default now(),
    atualizado_em    timestamptz    not null default now(),

    constraint ck_contraoferta_status check (status in ('pendente', 'aceita', 'recusada', 'invalidada')),
    constraint ck_contraoferta_valor check (valor_proposto > 0)
);

-- RN-02.1: uma rodada só — no máximo uma contraoferta PENDENTE por par pedido+entregador.
create unique index uk_contraoferta_pendente_por_par
    on contraoferta (pedido_id, entregador_id) where status = 'pendente';
create index ix_contraoferta_pedido on contraoferta (pedido_id);

-- RN-07.1: bloqueio é por estabelecimento, não afeta a relação do entregador com os demais.
create table bloqueio (
    id                  uuid primary key,
    estabelecimento_id  uuid not null references estabelecimento (usuario_id),
    entregador_id       uuid not null references entregador (usuario_id),
    motivo              text,
    criado_em           timestamptz not null default now(),

    constraint uk_bloqueio_par unique (estabelecimento_id, entregador_id)
);

create index ix_bloqueio_entregador on bloqueio (entregador_id);
