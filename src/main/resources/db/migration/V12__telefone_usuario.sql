-- Telefone de contato (RF-04.3, T-04): campo livre, editável direto via PATCH /me — não é dado verificado,
-- então não passa por moderação. Nulo para contas existentes; nenhum fluxo hoje o exige no cadastro (T-03).
alter table usuario add column telefone varchar(20);
