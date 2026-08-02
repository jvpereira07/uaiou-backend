-- Reversão manual de V12__telefone_usuario.sql.
alter table usuario drop column if exists telefone;
