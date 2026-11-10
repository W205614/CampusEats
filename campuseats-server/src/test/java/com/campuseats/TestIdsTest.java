package com.campuseats;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestIdsTest {
  @Test
  void serviceResponseIdsRetainAllLongDigits() {
    assertEquals(9_007_199_254_740_993L, TestIds.requireId("9007199254740993"));
  }

  @Test
  void malformedServiceResponseIdsFailImmediatelyWithTheirCause() {
    for (String invalid : new String[] {"not-an-id", "9223372036854775808"}) {
      var failure = assertThrows(AssertionError.class, () -> TestIds.requireId(invalid));
      assertTrue(failure.getMessage().contains(invalid));
      assertInstanceOf(NumberFormatException.class, failure.getCause());
    }
  }
}
