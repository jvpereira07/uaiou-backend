-- Reversão manual de V17__preferencia_notificacao.sql. Sem a tabela, todo canal volta a ser tratado
-- como ligado (o padrão de RF-08.9); nenhuma notificação deixa de ser persistida por isso.
drop table if exists preferencia_notificacao;
