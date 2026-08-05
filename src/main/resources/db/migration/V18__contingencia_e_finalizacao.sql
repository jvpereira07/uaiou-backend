-- RF-15.7 (antifraude de posição): divergência grande entre o lat/lng do corpo e a última posição
-- reportada não bloqueia (falso positivo por GPS ruim não pode travar entregador honesto na porta
-- do cliente) — só marca para revisão, com a magnitude registrada para o suporte julgar depois.
alter table evidencia_entrega add column revisao_necessaria boolean not null default false;
alter table evidencia_entrega add column divergencia_metros numeric(8, 2);

-- RF-16.5/RF-17.1: liberação escrita pelo SERVIDOR (a escada de contingência, T-16), nunca pelo
-- cliente — é o que impede o entregador de pular a validação por conveniência (RN-10.1).
alter table pedido add column contestavel_liberado boolean not null default false;

-- RF-16.5/RN-09.3: penalidade objetiva (telefone ausente + ausência de repasse), não julgamento —
-- por isso é aplicada pelo job, sem arbitragem. Fato bruto por evento; T-20 agrega em taxa de
-- contingência e T-22 expõe ao estabelecimento (RF-16.8: transparência antes da punição).
create table penalidade_estabelecimento (
    id                  uuid primary key,
    estabelecimento_id  uuid        not null references estabelecimento (usuario_id),
    pedido_id           uuid        not null references pedido (id),
    motivo              text        not null,
    pontos              integer     not null,
    criado_em           timestamptz not null default now(),

    constraint ck_penalidade_estabelecimento_pontos check (pontos > 0)
);

create index ix_penalidade_estabelecimento_alvo on penalidade_estabelecimento (estabelecimento_id, criado_em desc);
