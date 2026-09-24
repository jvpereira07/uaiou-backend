-- Modo feira (docs/feira/): código fixo por pedido. O operador define na publicação e deixa escrito
-- no ponto; o jogador só finaliza digitando-o — prova de que chegou lá, mais confiável que GPS
-- dentro de pavilhão. A instância copia o código do modelo. Nulo nos pedidos anteriores.
alter table pedido add column feira_codigo varchar(6);
alter table pedido add constraint ck_pedido_feira_codigo
    check (feira_codigo is null or feira_codigo ~ '^[0-9]{4,6}$');
