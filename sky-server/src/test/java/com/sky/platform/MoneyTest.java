package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void allocationConservesCentsAndStableTies() {
    assertArrayEquals(new long[] {1, 0, 0}, Money.allocate(1, new long[] {1, 1, 1}));
    var random = new Random(42);
    for (int run = 0; run < 1000; run++) {
      long[] weights = random.longs(20, 0, 100000).toArray();
      long total = Arrays.stream(weights).sum(), discount = random.nextLong(total + 1);
      long[] result = Money.allocate(discount, weights);
      assertEquals(discount, Arrays.stream(result).sum());
      for (int i = 0; i < weights.length; i++)
        assertTrue(result[i] >= 0 && result[i] <= weights[i]);
    }
  }

  @Test
  void quantityRefundsNeverLoseRemainders() {
    for (int quantity = 1; quantity <= 50; quantity++)
      for (int total = 0; total < 1000; total++) {
        long sum = 0;
        for (int i = 0; i < quantity; i++) sum += Money.quantityAmount(total, quantity, i, 1);
        assertEquals(total, sum);
      }
  }
}
