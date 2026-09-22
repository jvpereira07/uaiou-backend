-- Modo feira, segunda forma (docs/feira/): o que o admin publica é um MODELO, e cada captura gera
-- um pedido de verdade, atribuído àquele jogador.
--
-- A primeira forma (V28) tratava a captura como um registro à parte, e o jogador nunca virava dono
-- de um pedido. Isso custou o app inteiro: sem pedido atribuído, nenhuma tela do produto — vitrine,
-- entrega em andamento com GPS, coleta no estabelecimento, histórico — tinha o que mostrar, e a
-- demonstração virou duas telas soltas. Com o clone, o fluxo do produto roda inteiro por cima dos
-- dados da feira e o app volta a ser o app.

-- Liga a instância ao modelo. Nulo = pedido normal do produto, ou o próprio modelo.
alter table pedido add column feira_origem_id uuid references pedido (id);
create index ix_pedido_feira_origem on pedido (feira_origem_id);

-- O pedido que a captura gerou. Nulo só para as capturas criadas antes desta migration.
alter table feira_captura add column pedido_gerado_id uuid references pedido (id);

-- ck_pedido_feira_ou_produto (V28) exigia feira_limite > 0 em TODO pedido de feira. A instância não
-- tem estoque — estoque é do modelo —, então a regra passa a distinguir os dois: modelo tem limite e
-- não tem origem; instância tem origem e não tem limite.
alter table pedido drop constraint ck_pedido_feira_ou_produto;
alter table pedido add constraint ck_pedido_feira_ou_produto check (
    case
        when feira_recompensa is null then
            frete_proposto > 0
            and creditos_consumidos > 0
            and dest_bairro is not null
            and dest_rua is not null
            and dest_numero is not null
            and recebedor_nome is not null
            and feira_limite is null
            and feira_origem_id is null
        when feira_origem_id is null then
            -- Modelo: é dele que saem as cópias, e é ele que guarda o estoque do prêmio.
            frete_proposto = 0 and creditos_consumidos = 0 and feira_limite > 0
        else
            -- Instância: pedido real de um jogador, sem estoque próprio.
            frete_proposto = 0 and creditos_consumidos = 0 and feira_limite is null
    end
);

-- O estabelecimento UaiOu é o estande físico: o jogador vai até lá para a coleta, então ele precisa
-- de coordenada para aparecer no mapa do app. Semeado no centro de Santa Rita do Sapucaí (mesmo
-- ponto padrão do app) e ajustável pelo painel no dia, porque a posição da mesa só se sabe lá.
update estabelecimento
   set lat = -22.252600, long = -45.703300
 where usuario_id = '00000000-0000-0000-0000-0000000000fe';
