package com.campuseats.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CursorPagesTest {
  @Test
  void projectionPreservesExactLongCursorAndOnlyMapsTheReturnedPage() {
    var pages = new CursorPages(new DiscoveryCursor("cursor-projection-test-secret"));
    long lastId = 9_007_199_254_740_993L;
    var rows =
        List.of(Map.<String, Object>of("id", lastId), Map.<String, Object>of("id", lastId - 1));
    var source = pages.finish(rows, 1, "scope");
    var projected = new AtomicInteger();
    var result =
        source.map(
            row -> {
              projected.incrementAndGet();
              return row.get("id").toString();
            });
    assertEquals(List.of("9007199254740993"), result.records());
    assertEquals(1, projected.get());
    assertTrue(result.hasMore());
    assertEquals(source.nextCursor(), result.nextCursor());
    assertEquals(lastId, pages.before(result.nextCursor(), "scope"));
  }

  @Test
  void emptyProjectionKeepsTerminalPageWithoutACursor() {
    var pages = new CursorPages(new DiscoveryCursor("cursor-projection-test-secret"));
    var result = pages.finish(List.<Map<String, Object>>of(), 1, "scope").map(Object::toString);
    assertTrue(result.records().isEmpty());
    assertFalse(result.hasMore());
    assertNull(result.nextCursor());
  }
}
