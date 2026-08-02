-- Reversão manual de V1__identidade.sql. Rodar com o banco parado ou em janela de manutenção — nunca via
-- Flyway (Community não tem "undo"; isto é aplicado manualmente por um operador, de propósito).
drop table if exists admin;
drop table if exists entregador;
drop table if exists estabelecimento;
drop table if exists usuario;
