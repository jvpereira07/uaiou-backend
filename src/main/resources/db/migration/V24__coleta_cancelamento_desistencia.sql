-- T-26 — coleta no estabelecimento, cancelamento pelo estabelecimento e desistência do entregador.
--
-- "Chegou" não é status: é um fato (chegou_em) enquanto o pedido segue "aceito". Status novo só onde
-- muda o que cada parte pode fazer — "coletado" encerra o direito de cancelar e de desistir, e é a
-- pré-condição da finalização.
alter table pedido drop constraint ck_pedido_status;
alter table pedido add constraint ck_pedido_status check (status in (
    'criado', 'publicado', 'em_negociacao', 'aceito', 'coletado',
    'finalizado', 'finalizado_contestavel', 'cancelado'
));

alter table pedido drop constraint ck_pedido_atribuicao_coerente;
alter table pedido add constraint ck_pedido_atribuicao_coerente check (
    status not in ('aceito', 'coletado', 'finalizado', 'finalizado_contestavel')
    or (entregador_id is not null and frete_final is not null)
);

alter table pedido add column chegou_em timestamptz;
-- RF-26.2 — histerese da detecção automática: leituras consecutivas dentro do raio da loja.
alter table pedido add column leituras_no_raio_coleta smallint not null default 0;
alter table pedido add column coletado_em timestamptz;
alter table pedido add column cancelado_em timestamptz;
alter table pedido add column cancelamento_motivo varchar(30);
alter table pedido add column cancelamento_nota varchar(280);
alter table pedido add column lembrete_coleta_em timestamptz;

alter table pedido add constraint ck_pedido_coleta_coerente check (
    coletado_em is null or status in ('coletado', 'finalizado', 'finalizado_contestavel')
);
alter table pedido add constraint ck_pedido_cancelamento_coerente check (
    (status = 'cancelado' and cancelado_em is not null and cancelamento_motivo is not null)
    or (status <> 'cancelado' and cancelado_em is null and cancelamento_motivo is null)
);

-- RF-26.1 — a detecção roda a cada posição reportada, só sobre os pedidos aguardando chegada.
create index ix_pedido_aguardando_chegada on pedido (entregador_id)
    where status = 'aceito' and chegou_em is null;

-- RF-26.17 — a taxa de cancelamento é um lançamento do mesmo livro-razão, distinguido pelo tipo.
-- O percentual aplicado fica gravado: mudar a configuração não reescreve o passado (RF-26.18).
alter table lancamento_frete add column tipo varchar(30) not null default 'frete';
alter table lancamento_frete add column taxa_percentual numeric(5, 4);
alter table lancamento_frete add constraint ck_lancamento_frete_tipo check (
    (tipo = 'frete' and taxa_percentual is null)
    or (tipo = 'taxa_cancelamento' and taxa_percentual is not null)
);

-- RF-26.26 — o pedido volta limpo a "publicado", mas a desistência fica: é a base de score, limite e
-- disputa.
create table desistencia_pedido (
    id                uuid primary key,
    pedido_id         uuid         not null references pedido (id),
    entregador_id     uuid         not null references entregador (usuario_id),
    motivo            varchar(30)  not null,
    nota              varchar(280),
    aceito_em         timestamptz  not null,
    chegou_em         timestamptz,
    conta_penalidade  boolean      not null,
    criado_em         timestamptz  not null default now()
);

create index ix_desistencia_entregador on desistencia_pedido (entregador_id, criado_em);
create index ix_desistencia_pedido on desistencia_pedido (pedido_id, entregador_id);
