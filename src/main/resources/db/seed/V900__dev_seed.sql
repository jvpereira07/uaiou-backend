-- Seed de DESENVOLVIMENTO. Nunca aplicada em produção — não por convenção de nome, mas porque esta pasta
-- (db/seed) só entra em spring.flyway.locations no perfil "dev" (ver application-dev.yaml). Numerada
-- V900 para nunca colidir com as migrations reais de schema (V1..V10, com folga para crescer).
--
-- senha_hash é um placeholder sintaticamente válido (formato bcrypt), mas não corresponde a nenhuma senha
-- real — login por senha destes usuários só funcionará de fato quando T-03 gerar hashes reais.

insert into usuario (id, login, email, senha_hash, tipo, nome_exibicao, status) values
    ('00000000-0000-0000-0000-000000000001', 'admin', 'admin@uaiou.dev',
     '$2a$10$devSeedPlaceholderHashNotUsableForLogin.000000000000', 'admin', 'Admin UaiOu', 'ativo'),
    ('00000000-0000-0000-0000-000000000201', 'temperato_massas', 'temperato.massas@uaiou.dev',
     '$2a$10$devSeedPlaceholderHashNotUsableForLogin.000000000201', 'estabelecimento', 'Temperato Massas', 'ativo'),
    ('00000000-0000-0000-0000-000000000202', 'acai_avenida', 'acai.avenida@uaiou.dev',
     '$2a$10$devSeedPlaceholderHashNotUsableForLogin.000000000202', 'estabelecimento', 'Açaí Avenida', 'ativo'),
    ('00000000-0000-0000-0000-000000000301', 'joao_silva', 'joao.silva@uaiou.dev',
     '$2a$10$devSeedPlaceholderHashNotUsableForLogin.000000000301', 'entregador', 'João Silva', 'ativo'),
    ('00000000-0000-0000-0000-000000000302', 'kaique_entregas', 'kaique.entregas@uaiou.dev',
     '$2a$10$devSeedPlaceholderHashNotUsableForLogin.000000000302', 'entregador', 'Kaique Santos', 'ativo'),
    ('00000000-0000-0000-0000-000000000303', 'paola_entregas', 'paola.entregas@uaiou.dev',
     '$2a$10$devSeedPlaceholderHashNotUsableForLogin.000000000303', 'entregador', 'Paola Ferreira', 'ativo');

insert into admin (usuario_id, nivel) values
    ('00000000-0000-0000-0000-000000000001', 'pleno');

insert into estabelecimento (usuario_id, cnpj, nome_fantasia, bairro, rua, numero, cidade) values
    ('00000000-0000-0000-0000-000000000201', '11111111000191', 'Temperato Massas',
     'Jardim Independencia', 'Rua Beija-Flor', '45', 'Belo Horizonte'),
    ('00000000-0000-0000-0000-000000000202', '22222222000192', 'Açaí Avenida',
     'Centro', 'Avenida Central', '120', 'Belo Horizonte');

insert into entregador (usuario_id, cpf, veiculo_tipo, disponivel, score, entregas_realizadas) values
    ('00000000-0000-0000-0000-000000000301', '11111111111', 'moto', true, 87.50, 4),
    ('00000000-0000-0000-0000-000000000302', '22222222222', 'moto', true, 92.10, 12),
    ('00000000-0000-0000-0000-000000000303', '33333333333', 'bicicleta', false, 78.30, 2);

insert into plano (id, nome, cota_mensal_creditos, preco) values
    ('00000000-0000-0000-0000-000000000101', 'Essencial', 100, 99.90),
    ('00000000-0000-0000-0000-000000000102', 'Profissional', 300, 249.90);

insert into assinatura (id, estabelecimento_id, plano_id, status, proxima_renovacao) values
    ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000201',
     '00000000-0000-0000-0000-000000000101', 'ativa', current_date + interval '30 days'),
    ('00000000-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000202',
     '00000000-0000-0000-0000-000000000102', 'ativa', current_date + interval '30 days');

insert into carteira_creditos (estabelecimento_id, saldo_creditos) values
    ('00000000-0000-0000-0000-000000000201', 100),
    ('00000000-0000-0000-0000-000000000202', 300);

insert into transacao_credito (id, estabelecimento_id, tipo, quantidade, assinatura_id) values
    ('00000000-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000201',
     'cota_mensal', 100, '00000000-0000-0000-0000-000000000401'),
    ('00000000-0000-0000-0000-000000000502', '00000000-0000-0000-0000-000000000202',
     'cota_mensal', 300, '00000000-0000-0000-0000-000000000402');
