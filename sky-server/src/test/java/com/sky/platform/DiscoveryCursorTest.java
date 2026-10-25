package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import org.junit.jupiter.api.Test;

class DiscoveryCursorTest {
  @Test
  void signedPositionIsVersionedFilterBoundAndRejectsMalformedInputs() {
    var codec = new DiscoveryCursor("test-only-cursor-secret-at-least-thirty-two-bytes");
    var position = new DiscoveryCursor.Position(205, 1, 1001);
    String cursor = codec.encode("scope-a", position);
    assertEquals(position, codec.decode(cursor, "scope-a"));
    assertEquals(DiscoveryCursor.Position.START, codec.decode(null, "scope-a"));
    for (String invalid : new String[] {"", "wrong", cursor + "x", "x" + cursor, "a".repeat(513)})
      assertEquals("INVALID_CURSOR", assertThrows(BusinessException.class,
          () -> codec.decode(invalid, "scope-a")).code());
    assertEquals("INVALID_CURSOR", assertThrows(BusinessException.class,
        () -> codec.decode(cursor, "scope-b")).code());
    assertEquals("INVALID_CURSOR", assertThrows(BusinessException.class,
        () -> new DiscoveryCursor("another-key").decode(cursor, "scope-a")).code());
  }
}
