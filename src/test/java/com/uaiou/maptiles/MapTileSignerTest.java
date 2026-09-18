package com.uaiou.maptiles;

import static org.assertj.core.api.Assertions.assertThat;

import com.uaiou.maptiles.service.MapTileSigner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MapTileSignerTest {

  private static final Instant NOON = Instant.parse("2026-09-18T12:00:00Z");

  @Test
  void everyoneGetsTheSameTokenOnTheSameDay() {
    String morning = signerAt(Instant.parse("2026-09-18T00:00:01Z")).currentToken();
    String night = signerAt(Instant.parse("2026-09-18T23:59:59Z")).currentToken();

    assertThat(morning).isEqualTo(night);
  }

  @Test
  void acceptsTodayAndYesterdayOnly() {
    String yesterday = signerAt(NOON.minusSeconds(86_400)).currentToken();
    String twoDaysAgo = signerAt(NOON.minusSeconds(2 * 86_400)).currentToken();
    MapTileSigner today = signerAt(NOON);

    assertThat(today.isValid(today.currentToken())).isTrue();
    assertThat(today.isValid(yesterday)).isTrue();
    assertThat(today.isValid(twoDaysAgo)).isFalse();
  }

  @Test
  void rejectsForgedOrMalformedTokens() {
    MapTileSigner signer = signerAt(NOON);
    String valid = signer.currentToken();
    String day = valid.substring(0, valid.indexOf('.'));

    assertThat(signer.isValid(null)).isFalse();
    assertThat(signer.isValid("")).isFalse();
    assertThat(signer.isValid(day + ".AAAAAAAAAAAAAAAAAAAAAA")).isFalse();
    assertThat(signer.isValid("abc.def")).isFalse();
    assertThat(new MapTileSigner("outro-segredo", Clock.fixed(NOON, ZoneOffset.UTC)).isValid(valid))
        .isFalse();
  }

  private MapTileSigner signerAt(Instant instant) {
    return new MapTileSigner("segredo", Clock.fixed(instant, ZoneOffset.UTC));
  }
}
