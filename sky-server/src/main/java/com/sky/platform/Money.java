package com.sky.platform;

import java.math.*;
import java.util.*;

/** Integer cents; input order breaks equal-remainder ties. */
public final class Money {
  private Money() {}

  public static long cents(Object value) {
    return new BigDecimal(value.toString()).movePointRight(2).longValueExact();
  }

  public static BigDecimal decimal(long cents) {
    return BigDecimal.valueOf(cents, 2);
  }

  public static String text(long cents) {
    return decimal(cents).toPlainString();
  }

  public static long[] allocate(long amount, long[] weights) {
    long total = Arrays.stream(weights).sum();
    if (amount < 0 || Arrays.stream(weights).anyMatch(w -> w < 0) || amount > total)
      throw new IllegalArgumentException("Invalid allocation");
    long[] result = new long[weights.length];
    if (total == 0) return result;
    BigInteger divisor = BigInteger.valueOf(total);
    BigInteger[] remainders = new BigInteger[weights.length];
    long assigned = 0;
    for (int i = 0; i < weights.length; i++) {
      var qr =
          BigInteger.valueOf(amount)
              .multiply(BigInteger.valueOf(weights[i]))
              .divideAndRemainder(divisor);
      result[i] = qr[0].longValueExact();
      remainders[i] = qr[1];
      assigned += result[i];
    }
    List<Integer> indices = new ArrayList<>();
    for (int i = 0; i < weights.length; i++) indices.add(i);
    indices.sort(
        (a, b) -> {
          int c = remainders[b].compareTo(remainders[a]);
          return c == 0 ? Integer.compare(a, b) : c;
        });
    for (int i = 0; i < amount - assigned; i++) result[indices.get(i)]++;
    return result;
  }

  public static long quantityAmount(long total, int quantity, int offset, int count) {
    long base = total / quantity, extra = total % quantity;
    return base * count + Math.max(0, Math.min(extra - offset, count));
  }
}
