package com.campuseats;

public final class TestIds {
  private TestIds() {}

  public static long requireId(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      throw new AssertionError("Expected a numeric service response ID, got: " + value, ex);
    }
  }
}
