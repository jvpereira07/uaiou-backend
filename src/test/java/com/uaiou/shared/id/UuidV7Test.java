package com.uaiou.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void versionNibbleIsSeven() {
    UUID id = UuidV7.next();

    assertThat(id.version()).isEqualTo(7);
  }

  @Test
  void variantIsRfc4122() {
    UUID id = UuidV7.next();

    // java.util.UUID.variant() retorna 2 para o layout RFC (Leach-Salz).
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void laterTimestampAlwaysSortsAfterEarlierTimestamp() {
    UUID earlier = UuidV7.next(1_700_000_000_000L);
    UUID later = UuidV7.next(1_700_000_000_001L);

    assertThat(later).isGreaterThan(earlier);
  }

  @Test
  void sameMillisecondStillProducesDistinctValues() {
    UUID a = UuidV7.next(1_700_000_000_000L);
    UUID b = UuidV7.next(1_700_000_000_000L);

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void toStringRoundTripsThroughUuidFromString() {
    UUID id = UuidV7.next();

    assertThat(UUID.fromString(id.toString())).isEqualTo(id);
  }

  @Test
  void embeddedTimestampMatchesTheInputMillis() {
    long millis = 1_700_000_123_456L;
    UUID id = UuidV7.next(millis);

    long extracted = id.getMostSignificantBits() >>> 16;
    assertThat(extracted).isEqualTo(millis);
  }
}
