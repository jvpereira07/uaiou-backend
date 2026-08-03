-- T-10 (RF-10.4/RF-10.9): "accuracy" da leitura de GPS, em metros. O schema original (V1) guardava
-- lat/long/localizacao_em mas não a precisão — sem ela não dá para cumprir RF-10.9 ("leitura com precisão
-- pior que o raio do geofence não deve habilitar finalização", consumido por T-15): uma posição dentro do
-- geofence com 500 m de erro não prova nada.
alter table entregador add column accuracy numeric(7, 2);

-- T-10 (RF-10.1/RF-10.10): marca de tempo de quando a disponibilidade foi ligada — é o "since" que
-- PUT /me/availability devolve (api/usuarios.md) e o relógio que alimenta horas disponíveis/utilização.
-- Nulo sempre que disponivel = false; o par (disponivel, disponivel_desde) nasce e morre junto.
alter table entregador add column disponivel_desde timestamptz;

alter table entregador add constraint ck_entregador_accuracy_nao_negativa check (accuracy is null or accuracy >= 0);

-- Sem índice novo aqui: a consulta de elegibilidade ("disponível E com posição recente", RF-10.6/RF-10.7)
-- e a do job de expiração (RF-10.8) já são atendidas pelo ix_entregador_disponivel_localizacao criado em
-- V1__identidade.sql, que é parcial em `where disponivel` e ordenado por (disponivel, localizacao_em).
