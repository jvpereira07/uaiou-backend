-- Reversão manual de V13__documento_cadastro_upload_unico.sql.
alter table documento_cadastro drop constraint if exists uk_documento_cadastro_upload;
