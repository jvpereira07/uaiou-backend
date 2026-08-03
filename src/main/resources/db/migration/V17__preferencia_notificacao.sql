-- T-08 (RF-08.9): preferências de notificação por CANAL (push/e-mail/SMS), não por tipo de evento —
-- é o que o contrato de PUT /me/notification-preferences expõe (notificacoes.md).
--
-- Uma linha por usuário (PK = FK), mesmo padrão 1:1 de carteira_creditos. A ausência de linha
-- significa "tudo ligado": o padrão é receber, e a linha só existe depois que o usuário mexeu.
--
-- Os canais críticos NÃO são modelados aqui de propósito: "mandatory" é propriedade do TIPO de
-- evento (contingência, sanção, disputa), não do usuário, e vive no catálogo em código. Guardar um
-- booleano por usuário para algo que ele nunca pode desligar só criaria um estado capaz de mentir.
create table preferencia_notificacao (
    usuario_id     uuid primary key references usuario (id),
    push           boolean     not null default true,
    email          boolean     not null default true,
    sms            boolean     not null default true,
    criado_em      timestamptz not null default now(),
    atualizado_em  timestamptz not null default now()
);
