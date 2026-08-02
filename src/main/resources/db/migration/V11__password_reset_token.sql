-- Faltava no desenho original de T-02 — RF-03.11 precisa de um token de uso único e validade curta para
-- confirmar a redefinição de senha. token_hash, nunca o token em claro (mesmo raciocínio de refresh_token).
create table password_reset_token (
    id           uuid primary key,
    usuario_id   uuid         not null references usuario (id),
    token_hash   varchar(255) not null,
    expira_em    timestamptz  not null,
    usado_em     timestamptz,
    criado_em    timestamptz  not null default now(),

    constraint uk_password_reset_token_hash unique (token_hash)
);

create index ix_password_reset_token_usuario on password_reset_token (usuario_id);
