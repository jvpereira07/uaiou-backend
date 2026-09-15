-- O entregador pode aceitar mais de uma forma de pagamento: a coluna única de V22 vira uma tabela
-- (uma linha por forma aceita). A PK composta impede a mesma forma duas vezes, e o check mantém o
-- vocabulário fechado de V22. Quem já tinha escolhido uma forma continua com ela.
create table entregador_forma_pagamento (
  usuario_id uuid not null references entregador (usuario_id) on delete cascade,
  forma_pagamento varchar(16) not null,
  primary key (usuario_id, forma_pagamento),
  constraint ck_entregador_forma_pagamento_valor
    check (forma_pagamento in ('dinheiro', 'credito', 'debito', 'pix'))
);

insert into entregador_forma_pagamento (usuario_id, forma_pagamento)
select usuario_id, forma_pagamento from entregador where forma_pagamento is not null;

alter table entregador drop constraint ck_entregador_forma_pagamento;
alter table entregador drop column forma_pagamento;
