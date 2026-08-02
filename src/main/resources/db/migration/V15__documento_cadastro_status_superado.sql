-- RF-06.5 (T-06): editar um campo verificado já aprovado (T-04) ou reenviar um documento cria uma nova
-- linha e marca a anterior como superada — "superado" é distinto de "rejeitado" (nunca foi negado, só
-- deixou de ser a versão vigente) e distinto de deletar (o histórico é preservado, RF-06.4).
alter table documento_cadastro drop constraint ck_documento_cadastro_status;
alter table documento_cadastro add constraint ck_documento_cadastro_status check (status_aprovacao in ('pendente', 'aprovado', 'rejeitado', 'superado'));
