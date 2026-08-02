-- Identidade (CTI): usuario é a tabela base — autenticação, status de moderação e perfil comum aos três
-- tipos. estabelecimento/entregador/admin usam shared primary key (usuario_id é PK e FK ao mesmo tempo):
-- não existe linha-filha sem linha-base, e uma consulta "quem é este usuário" é sempre um JOIN direto.
--
-- Nenhuma coluna "id" tem DEFAULT de geração no banco: os ids são UUID v7 gerados pela aplicação antes do
-- insert (RF-02.1) — o serviço precisa do id para montar eventos e links antes de persistir.

create table usuario (
    id             uuid primary key,
    login          varchar(60)  not null,
    email          varchar(160) not null,
    senha_hash     varchar(255),
    google_id      varchar(120),
    tipo           varchar(20)  not null,
    nome_exibicao  varchar(120) not null,
    status         varchar(20)  not null default 'pendente',
    criado_em      timestamptz  not null default now(),
    atualizado_em  timestamptz  not null default now(),

    constraint uk_usuario_login      unique (login),
    constraint uk_usuario_email      unique (email),
    constraint uk_usuario_google_id  unique (google_id),
    constraint ck_usuario_tipo       check (tipo in ('estabelecimento', 'entregador', 'admin')),
    constraint ck_usuario_status     check (status in ('pendente', 'ativo', 'suspenso', 'banido')),
    -- usuário sem nenhuma forma de autenticar é estado inválido (conta só-senha, só-Google ou as duas).
    constraint ck_usuario_credencial check (senha_hash is not null or google_id is not null)
);

create table estabelecimento (
    usuario_id      uuid primary key references usuario (id),
    cnpj            varchar(14)   not null,
    nome_fantasia   varchar(120),
    logo_object_key varchar(255),
    bairro          varchar(80),
    rua             varchar(120),
    numero          varchar(10),
    cidade          varchar(80),
    cep             varchar(8),
    score           numeric(5, 2),
    score_componentes jsonb,
    criado_em       timestamptz not null default now(),
    atualizado_em   timestamptz not null default now(),

    constraint uk_estabelecimento_cnpj unique (cnpj)
);

create table entregador (
    usuario_id           uuid primary key references usuario (id),
    cpf                  varchar(11)   not null,
    cnh                  varchar(20),
    veiculo_tipo         varchar(20),
    veiculo_placa        varchar(8),
    disponivel           boolean       not null default false,
    lat                  numeric(9, 6),
    long                 numeric(9, 6),
    localizacao_em       timestamptz,
    score                numeric(5, 2),
    score_componentes    jsonb,
    entregas_realizadas  integer       not null default 0,
    criado_em            timestamptz   not null default now(),
    atualizado_em        timestamptz   not null default now(),

    constraint uk_entregador_cpf unique (cpf),
    constraint ck_entregador_entregas_realizadas check (entregas_realizadas >= 0)
);

-- Elegibilidade (T-11): só entregadores disponíveis entram no filtro por proximidade. Índice parcial —
-- quem está indisponível nunca é candidato, não faz sentido indexá-lo aqui.
create index ix_entregador_disponivel_localizacao
    on entregador (disponivel, localizacao_em) where disponivel;

create table admin (
    usuario_id     uuid primary key references usuario (id),
    nivel          varchar(20) not null default 'pleno',
    criado_em      timestamptz not null default now(),
    atualizado_em  timestamptz not null default now(),

    -- níveis de admin ainda são TODO(dono) na documentação de negócio; "pleno" é o único usado até T-07
    -- decidir a distinção atendente/pleno/superadmin.
    constraint ck_admin_nivel check (nivel in ('atendente', 'pleno', 'superadmin'))
);
