-- Reversão manual de V14__usuario_status_rejeitado.sql. Assume que nenhuma linha está em 'rejeitado';
-- se houver, seria preciso migrá-las para outro status antes de reaplicar o CHECK antigo.
alter table usuario drop constraint ck_usuario_status;
alter table usuario add constraint ck_usuario_status check (status in ('pendente', 'ativo', 'suspenso', 'banido'));
