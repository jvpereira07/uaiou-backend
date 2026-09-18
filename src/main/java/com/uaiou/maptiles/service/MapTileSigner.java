package com.uaiou.maptiles.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Token que autoriza as URLs de tile, glifo e sprite.
 *
 * <p>O MapLibre busca esses recursos sozinho, sem o {@code Authorization} do app — então a
 * autorização viaja na URL. O token só é entregue dentro do estilo, e o estilo só sai para quem tem
 * sessão.
 *
 * <p><strong>Por dia, não por usuário:</strong> todo mundo recebe a mesma URL no mesmo dia, e isso
 * é o que deixa o cache do aparelho acertar (a chave do cache nativo é a URL inteira). O token de
 * ontem continua valendo, para quem abriu a navegação perto da meia-noite não perder o mapa.
 */
public class MapTileSigner {

  private static final String ALGORITHM = "HmacSHA256";
  private static final String DOMAIN = "uaiou-map-tiles:";
  private static final int SIGNATURE_BYTES = 16;

  private final byte[] secret;
  private final Clock clock;

  public MapTileSigner(String secret, Clock clock) {
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.clock = clock;
  }

  public String currentToken() {
    return token(today());
  }

  public boolean isValid(String token) {
    if (token == null) {
      return false;
    }
    int separator = token.indexOf('.');
    if (separator <= 0) {
      return false;
    }
    long day;
    try {
      day = Long.parseLong(token.substring(0, separator));
    } catch (NumberFormatException e) {
      return false;
    }
    long today = today();
    if (day != today && day != today - 1) {
      return false;
    }
    return MessageDigest.isEqual(
        token.getBytes(StandardCharsets.UTF_8), token(day).getBytes(StandardCharsets.UTF_8));
  }

  private long today() {
    return LocalDate.now(clock.withZone(ZoneOffset.UTC)).toEpochDay();
  }

  private String token(long day) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret, ALGORITHM));
      byte[] full = mac.doFinal((DOMAIN + day).getBytes(StandardCharsets.UTF_8));
      String signature =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(Arrays.copyOf(full, SIGNATURE_BYTES));
      return day + "." + signature;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 indisponível na JVM", e);
    }
  }
}
