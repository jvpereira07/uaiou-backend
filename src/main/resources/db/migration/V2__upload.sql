-- Criada cedo (logo após identidade) porque documento_cadastro (V3) e evidencia_entrega (V7) referenciam
-- upload por FK. object_key é o caminho no MinIO — a URL nunca é persistida, é assinada a cada leitura.
--
-- Nomenclatura em português para consistência com o resto do schema; o contrato HTTP (api/uploads.md) usa
-- os nomes em inglês (IDENTITY_DOCUMENT etc.) — a tradução é responsabilidade do serviço, não do banco.
create table upload (
    id            uuid primary key,
    usuario_id    uuid         not null references usuario (id),
    purpose       varchar(30)  not null,
    content_type  varchar(100) not null,
    size_bytes    bigint       not null,
    status        varchar(20)  not null default 'awaiting_upload',
    object_key    varchar(255) not null,
    checksum      varchar(128),
    expira_em     timestamptz  not null,
    criado_em     timestamptz  not null default now(),
    atualizado_em timestamptz  not null default now(),

    constraint uk_upload_object_key unique (object_key),
    constraint ck_upload_status check (status in ('awaiting_upload', 'ready')),
    constraint ck_upload_purpose check (purpose in (
        'documento_identidade', 'cnh', 'documento_veiculo',
        'documento_cnpj', 'logo_estabelecimento', 'comprovante_entrega'
    )),
    constraint ck_upload_size_bytes check (size_bytes > 0)
);

create index ix_upload_usuario on upload (usuario_id);
