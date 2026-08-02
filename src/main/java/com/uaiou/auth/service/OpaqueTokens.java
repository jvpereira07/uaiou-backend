package com.uaiou.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Gera e cifra os tokens opacos (refresh, redefinição de senha) — nunca JWT, nunca persistidos em
 * claro (RF-03.2/03.8). SHA-256 simples é o bastante aqui: ao contrário de senha, o valor de
 * entrada já é aleatório de alta entropia — não há o que um hash lento (BCrypt) defenderia contra
 * força bruta.
 */
final class OpaqueTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private OpaqueTokens() {}

  static String generate(String prefix) {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static String hash(String rawToken) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 indisponível na JVM", e);
    }
  }
}
