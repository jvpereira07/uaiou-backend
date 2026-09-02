-- A-05/A-08 (app do entregador): o endereço do estabelecimento (V1__identidade.sql) sempre foi só
-- texto livre (bairro/rua/numero/cidade/cep), sem geocodificação — diferente do destino do pedido
-- (dest_lat/dest_long, V5), que é geocodificado na criação porque ancora o geofence da finalização
-- (RN-08.1). Sem coordenada aqui, o app do entregador não tinha como orientar o trajeto até a
-- retirada, só até a entrega.
--
-- Mesma precisão de V5__pedido_negociacao.sql (numeric(9,6)) por consistência — não é geofence
-- server-side como o do pedido, então nenhuma coluna de raio/validação entra junto.
alter table estabelecimento add column lat numeric(9, 6);
alter table estabelecimento add column long numeric(9, 6);
