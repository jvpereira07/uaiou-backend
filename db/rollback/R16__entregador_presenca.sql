-- Reversão manual de V16__entregador_presenca.sql. Perde a precisão das leituras de GPS e o relógio de
-- disponibilidade — ambos só existem a partir de T-10, nenhum outro módulo depende deles.
-- ix_entregador_disponivel_localizacao NÃO é removido aqui: ele vem de V1__identidade.sql, não de V16.
alter table entregador drop constraint if exists ck_entregador_accuracy_nao_negativa;
alter table entregador drop column if exists disponivel_desde;
alter table entregador drop column if exists accuracy;
