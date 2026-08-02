-- RF-06.4 (T-06): reenvio de documento após rejeição volta usuario.status para pendente — precisa existir
-- "rejeitado" para sair dele. Quem PASSA a produzir esse valor pela primeira vez é a decisão do admin
-- (T-07, ainda não implementada); T-06 só consome a transição de volta (rejeitado → pendente).
alter table usuario drop constraint ck_usuario_status;
alter table usuario add constraint ck_usuario_status check (status in ('pendente', 'ativo', 'suspenso', 'banido', 'rejeitado'));
