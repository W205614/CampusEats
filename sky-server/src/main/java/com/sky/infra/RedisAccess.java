package com.sky.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Single-instance, bounded Redis admission with one recovery probe after a failed command. */
@Component
public class RedisAccess {
  private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(5);
  private final LongSupplier ticker;
  private final MeterRegistry metrics;
  private long openUntil, generation;
  private int active;
  private boolean probe;

  @Autowired
  public RedisAccess(MeterRegistry metrics) {
    this(System::nanoTime, metrics);
  }

  public RedisAccess() {
    this(System::nanoTime, null);
  }

  RedisAccess(LongSupplier ticker, MeterRegistry metrics) {
    this.ticker = ticker;
    this.metrics = metrics;
    if (metrics != null) {
      metrics.gauge("campus.redis.circuit.open", this, value -> value.isOpen() ? 1 : 0);
      metrics.gauge("campus.redis.calls.active", this, RedisAccess::active);
    }
  }

  public <T> T call(Supplier<T> action) {
    long permit = enter();
    try {
      T result = action.get();
      synchronized (this) {
        // A late successful command must not close a circuit opened by a newer failure.
        if (permit == generation) {
          openUntil = 0;
          probe = false;
        }
      }
      return result;
    } catch (RuntimeException ex) {
      synchronized (this) {
        generation++;
        openUntil = ticker.getAsLong() + RETRY_NANOS;
        probe = false;
      }
      count("campus.redis.calls.failed");
      throw ex;
    } finally {
      synchronized (this) { active--; }
    }
  }

  private synchronized long enter() {
    if (active >= 4 || openUntil != 0 && (ticker.getAsLong() - openUntil < 0 || probe)) {
      count("campus.redis.calls.skipped");
      throw new Unavailable();
    }
    if (openUntil != 0) {
      probe = true;
      count("campus.redis.recovery.probes");
    }
    active++;
    return generation;
  }

  public synchronized boolean isOpen() { return openUntil != 0; }
  private synchronized int active() { return active; }
  private void count(String name) { if (metrics != null) metrics.counter(name).increment(); }

  public static final class Unavailable extends RuntimeException {
    private Unavailable() { super("Redis temporarily bypassed", null, false, false); }
  }
}
