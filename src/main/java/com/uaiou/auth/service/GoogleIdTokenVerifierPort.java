package com.uaiou.auth.service;

import java.util.Optional;

/**
 * Porta de verificação do ID token do Google (RF-03.6) — abstraída para os testes conseguirem
 * injetar um fake em vez de depender de token real do Google (impossível de forjar em CI). A
 * implementação de produção ({@link GoogleIdTokenVerifierAdapter}) verifica de verdade contra as
 * chaves públicas do emissor.
 */
public interface GoogleIdTokenVerifierPort {

  /** Vazio se o token for inválido, expirado ou tiver assinatura/audiência erradas. */
  Optional<GoogleIdentity> verify(String idToken);
}
