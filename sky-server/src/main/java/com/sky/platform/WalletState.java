package com.sky.platform;

import java.time.LocalDateTime;

/** Display-only expiry; reservation and redemption retain their persisted lifecycle state. */
final class WalletState {
  private WalletState() {}

  static String display(String state, LocalDateTime endsAt, LocalDateTime at) {
    return "AVAILABLE".equals(state) && !endsAt.isAfter(at) ? "EXPIRED" : state;
  }
}
