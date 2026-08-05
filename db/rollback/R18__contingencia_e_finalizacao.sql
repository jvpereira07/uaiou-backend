drop table if exists penalidade_estabelecimento;
alter table pedido drop column if exists contestavel_liberado;
alter table evidencia_entrega drop column if exists divergencia_metros;
alter table evidencia_entrega drop column if exists revisao_necessaria;
