-- Foto de perfil do entregador. O estabelecimento já tinha logo (V1, logo_object_key); o entregador
-- não tinha imagem nenhuma — e é justamente a foto dele que a loja precisa ver quando há vários
-- entregadores na porta (T-26, RF-26.6).
alter table upload drop constraint ck_upload_purpose;
alter table upload add constraint ck_upload_purpose check (purpose in (
    'documento_identidade', 'cnh', 'documento_veiculo',
    'documento_cnpj', 'logo_estabelecimento', 'comprovante_entrega', 'foto_entregador'
));

alter table entregador add column foto_object_key varchar(255);
