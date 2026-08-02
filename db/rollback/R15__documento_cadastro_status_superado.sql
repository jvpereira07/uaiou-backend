-- Reversão manual de V15__documento_cadastro_status_superado.sql. Assume que nenhuma linha está em
-- 'superado'; se houver, seria preciso migrá-las antes de reaplicar o CHECK antigo.
alter table documento_cadastro drop constraint ck_documento_cadastro_status;
alter table documento_cadastro add constraint ck_documento_cadastro_status check (status_aprovacao in ('pendente', 'aprovado', 'rejeitado'));
