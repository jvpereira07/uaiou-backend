package com.uaiou.delivery;

/**
 * {@code contingencia_otp.resultado} (V7__validacao_entrega.sql). Interno ao motor de contingência
 * — não trafega em JSON por si só (o que a rota expõe é {@code step}/{@code contestableReleased}),
 * por isso sem conversão de nome, só de valor de banco.
 */
public enum ContingencyResult {
  RESENT,
  NOTIFIED,
  DISPATCHED,
  NO_PHONE,
  NO_RESPONSE,
  EXPIRED
}
