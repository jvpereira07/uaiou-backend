-- Perfil do entregador: forma de pagamento que ele aceita receber pelo frete. Nulável — quem já
-- existia antes desta migration ainda não escolheu, e isso não é um estado inválido.
alter table entregador add column forma_pagamento varchar(16);
alter table entregador add constraint ck_entregador_forma_pagamento
  check (forma_pagamento in ('dinheiro', 'credito', 'debito', 'pix'));
