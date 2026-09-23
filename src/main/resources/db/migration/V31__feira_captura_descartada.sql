-- Modo feira (docs/feira/): o operador descarta uma captura que não vai mais andar — teste, jogador
-- que sumiu, pedido que ficou pendurado. Marca em vez de apagar: some do balcão, mas o histórico de
-- quem pegou o quê continua inteiro.
alter table feira_captura add column descartada_em timestamptz;
