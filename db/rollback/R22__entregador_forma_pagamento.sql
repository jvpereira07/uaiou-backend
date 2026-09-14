alter table entregador drop constraint if exists ck_entregador_forma_pagamento;
alter table entregador drop column if exists forma_pagamento;
