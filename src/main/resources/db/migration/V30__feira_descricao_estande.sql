-- Modo feira (docs/feira/): descrição em texto do ponto do estande. Num salão o pino no mapa não
-- basta para achar a mesa ("ao lado da entrada do bloco B"), e a posição só se sabe no dia, então
-- o operador edita pelo painel junto com a coordenada.
alter table estabelecimento add column feira_descricao_local varchar(200);
