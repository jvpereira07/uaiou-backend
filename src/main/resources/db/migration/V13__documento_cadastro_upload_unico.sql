-- "Vínculo único" (api/uploads.md, Regras): cada upload é consumido por um único recurso. Sem esta
-- constraint, nada impede reenviar o mesmo uploadId em duas edições de campo verificado (PATCH /me, T-04) e
-- criar dois documento_cadastro pendentes para a mesma prova — unicidade checada no banco, não só na
-- aplicação, pelo mesmo motivo de auth.md (RF-03.1): duas requisições concorrentes abririam uma corrida.
alter table documento_cadastro add constraint uk_documento_cadastro_upload unique (upload_id);
