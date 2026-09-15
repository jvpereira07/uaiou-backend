-- Volta para a coluna única de V22. Quem aceitava mais de uma forma fica só com uma delas
-- (a primeira em ordem alfabética) — a perda é inevitável ao voltar para um valor só.
alter table entregador add column forma_pagamento varchar(16);
alter table entregador add constraint ck_entregador_forma_pagamento
  check (forma_pagamento in ('dinheiro', 'credito', 'debito', 'pix'));

update entregador e
set forma_pagamento = (
  select min(f.forma_pagamento) from entregador_forma_pagamento f where f.usuario_id = e.usuario_id
);

drop table if exists entregador_forma_pagamento;
