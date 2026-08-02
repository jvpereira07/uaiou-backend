package com.uaiou.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Gera UUID v7 (RFC 9562): 48 bits de timestamp Unix em milissegundos + bits aleatórios, ordenável
 * por criação. É a PK de toda linha inserida pela aplicação (RF-02.1) — {@code UUID.randomUUID()}
 * do JDK gera v4 (puramente aleatório), que fragmenta o índice da chave primária; v7 mantém as
 * inserções sequencialmente próximas na árvore.
 */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UuidV7() {}

  public static UUID next() {
    return next(System.currentTimeMillis());
  }

  /** Pacote-visível para o teste controlar o timestamp e verificar a propriedade de ordenação. */
  static UUID next(long unixMillis) {
    byte[] randomBytes = new byte[10];
    RANDOM.nextBytes(randomBytes);

    long mostSigBits = (unixMillis & 0xFFFFFFFFFFFFL) << 16;
    // versão 7 nos 4 bits altos do segundo bloco de 16 bits
    mostSigBits |= 0x7000L;
    mostSigBits |= (randomBytes[0] & 0x0FL) << 8;
    mostSigBits |= (randomBytes[1] & 0xFFL);

    long leastSigBits = 0L;
    // variante RFC (10) nos 2 bits altos
    leastSigBits |= 0x8000000000000000L;
    leastSigBits |= ((long) (randomBytes[2] & 0x3F)) << 56;
    for (int i = 3; i < 10; i++) {
      leastSigBits |= ((long) (randomBytes[i] & 0xFF)) << (8 * (9 - i));
    }

    return new UUID(mostSigBits, leastSigBits);
  }
}
