-- RF-21.6: encerramento pode amarrar o chamado ao ajuste financeiro que o resolveu — o par da
-- RN-13.2, fechando os dois lados (o ajuste também aponta para o chamado via reference, já
-- suportado). Polimórfico como referencia_tipo/referencia_id, mesmo motivo: não acopla o schema a
-- cada tipo de ajuste que passar a existir.
alter table chamado_suporte add column ajuste_tipo varchar(40);
alter table chamado_suporte add column ajuste_id uuid;
