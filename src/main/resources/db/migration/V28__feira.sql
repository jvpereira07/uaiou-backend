-- Modo feira (docs/feira/): versão de demonstração do UaiOu para estande de feira tecnológica.
-- Jogador entra só com nome + nome de usuário, o admin publica pedidos com uma localização, uma
-- recompensa em texto ("1 Bis") e um estoque; várias pessoas capturam o mesmo pedido, cada uma
-- uma vez só, e o operador marca a entrega do prêmio no painel.
--
-- Esta migration vive numa branch que NUNCA será mesclada em main (feature/feira-tecnologica).
-- Por isso ela pode relaxar constraints do produto: o objetivo é que o mesmo schema aceite as
-- duas formas de pedido, com o pedido de feira identificado por feira_recompensa não-nulo.

-- ---------------------------------------------------------------------------------------------
-- Jogador: nome de usuário como identidade, sem CPF
-- ---------------------------------------------------------------------------------------------

alter table entregador add column username varchar(20);
alter table entregador alter column cpf drop not null;

-- Parcial: entregadores do produto não têm username e não disputam esse espaço de nomes.
create unique index ux_entregador_username on entregador (username) where username is not null;

-- ---------------------------------------------------------------------------------------------
-- Pedido de feira: localização + recompensa em texto + estoque
-- ---------------------------------------------------------------------------------------------

alter table pedido add column feira_recompensa varchar(60);
alter table pedido add column feira_limite     integer;
alter table pedido add column feira_capturas   integer not null default 0;
-- Encerramento próprio, em vez de reusar status 'cancelado': cancelamento do produto exige motivo
-- (ck_pedido_cancelamento_coerente, V24) e alimenta métrica de cancelamento da loja. Um pedido de
-- feira que acabou não é um pedido cancelado.
alter table pedido add column feira_encerrado_em timestamptz;

-- O que o pedido oferece na feira é um prêmio físico escrito pelo operador na hora, não dinheiro:
-- texto livre, sem moeda, sem arredondamento, e trocar a brincadeira não exige deploy.
-- Endereço e destinatário não existem num salão — só o ponto no mapa.
alter table pedido alter column dest_bairro     drop not null;
alter table pedido alter column dest_rua        drop not null;
alter table pedido alter column dest_numero     drop not null;
alter table pedido alter column recebedor_nome  drop not null;

alter table pedido drop constraint ck_pedido_frete_proposto;
alter table pedido drop constraint ck_pedido_creditos_consumidos;

-- As invariantes do produto continuam valendo para pedido não-feira; o pedido de feira troca-as
-- pelas suas (recompensa preenchida, estoque positivo, frete e créditos zerados).
alter table pedido add constraint ck_pedido_feira_ou_produto check (
    case when feira_recompensa is null then
        frete_proposto > 0
        and creditos_consumidos > 0
        and dest_bairro is not null
        and dest_rua is not null
        and dest_numero is not null
        and recebedor_nome is not null
        and feira_limite is null
    else
        frete_proposto = 0
        and creditos_consumidos = 0
        and feira_limite > 0
    end
);

-- Estoque nunca passa do limite nem fica negativo: o decremento acontece sob trava no pedido
-- (select ... for update) na mesma transação da captura, e esta constraint é a rede embaixo.
alter table pedido add constraint ck_pedido_feira_capturas check (
    feira_capturas >= 0 and (feira_limite is null or feira_capturas <= feira_limite)
);

-- ---------------------------------------------------------------------------------------------
-- Captura: uma por pessoa por pedido
-- ---------------------------------------------------------------------------------------------

create table feira_captura (
    id            uuid primary key,
    pedido_id     uuid        not null references pedido (id),
    entregador_id uuid        not null references entregador (usuario_id),
    criado_em     timestamptz not null default now(),
    -- Nulo = capturou e ainda não retirou o prêmio. É o que o operador olha no balcão.
    entregue_em   timestamptz,

    -- A regra do jogo mora aqui, não na aplicação: a disputa é concorrente por natureza e
    -- checar antes de inserir perde a corrida. O 409 "ja_capturado" é a tradução desta violação.
    constraint ux_feira_captura unique (pedido_id, entregador_id)
);

create index ix_feira_captura_pedido on feira_captura (pedido_id, criado_em);
create index ix_feira_captura_entregador on feira_captura (entregador_id, criado_em desc);

-- ---------------------------------------------------------------------------------------------
-- Estabelecimento fixo "UaiOu": dono de todos os pedidos da feira
-- ---------------------------------------------------------------------------------------------

-- Id determinístico: o serviço da feira referencia esta linha por constante, sem lookup por nome.
-- senha_hash é um placeholder com formato bcrypt válido que não corresponde a senha nenhuma —
-- este estabelecimento nunca faz login, quem opera é o admin.
insert into usuario (id, login, email, senha_hash, tipo, nome_exibicao, status) values
    ('00000000-0000-0000-0000-0000000000fe', 'uaiou_feira', 'feira@uaiou.dev',
     '$2a$10$feiraPlaceholderHashNotUsableForLogin.0000000000fe',
     'estabelecimento', 'UaiOu', 'ativo');

insert into estabelecimento (usuario_id, cnpj, nome_fantasia, cidade) values
    ('00000000-0000-0000-0000-0000000000fe', '00000000000191', 'UaiOu', 'Belo Horizonte');
