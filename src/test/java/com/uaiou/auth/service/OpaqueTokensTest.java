package com.uaiou.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpaqueTokensTest {

  @Test
  void generatedTokenCarriesThePrefixAndIsHighEntropy() {
    String token = OpaqueTokens.generate("rt_");

    assertThat(token).startsWith("rt_");
    assertThat(token.length()).isGreaterThan(40);
  }

  @Test
  void generateNeverRepeatsAcrossManyCalls() {
    Set<String> tokens = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      tokens.add(OpaqueTokens.generate("rt_"));
    }

    assertThat(tokens).hasSize(1000);
  }

  @Test
  void hashIsDeterministicAndDifferentInputsProduceDifferentHashes() {
    String hash1 = OpaqueTokens.hash("mesmo-valor");
    String hash2 = OpaqueTokens.hash("mesmo-valor");
    String hash3 = OpaqueTokens.hash("valor-diferente");

    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).isNotEqualTo(hash3);
  }

  @Test
  void hashNeverEqualsTheRawToken() {
    String raw = OpaqueTokens.generate("pr_");

    assertThat(OpaqueTokens.hash(raw)).isNotEqualTo(raw);
  }
}
