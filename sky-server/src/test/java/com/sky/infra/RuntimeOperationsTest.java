package com.sky.infra;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class RuntimeOperationsTest {
  static class MutableClock extends Clock {
    Instant value = Instant.parse("2026-10-19T12:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return value;
    }

    void advance(long seconds) {
      value = value.plusSeconds(seconds);
    }
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> job(RuntimeOperations ops, String name) {
    return (Map<String, Object>) ((Map<?, ?>) ops.snapshot().get("jobs")).get(name);
  }

  @Test
  void startupFailureRecoveryAndRestartAreDistinguished() {
    var clock = new MutableClock();
    var ops = new RuntimeOperations(clock, true);
    assertEquals("STARTING", job(ops, "metrics").get("status"));
    clock.advance(61);
    assertEquals("STALE", job(ops, "metrics").get("status"));
    assertEquals("STARTING", job(ops, "checkoutExpiry").get("status"));
    ops.run(
        "metrics",
        r -> {
          r.failed(new IllegalStateException(), "sample");
          assertEquals("FAILED", job(ops, "metrics").get("status"));
        });
    assertEquals("FAILED", job(ops, "metrics").get("status"));
    assertNull(job(ops, "metrics").get("lastSuccessAt"));
    ops.run("metrics", r -> {});
    assertEquals("OK", job(ops, "metrics").get("status"));
    assertNotNull(job(ops, "metrics").get("lastSuccessAt"));
    clock.advance(660);
    assertEquals("STALE", job(ops, "maintenance").get("status"));
    assertEquals("STARTING", job(new RuntimeOperations(clock, true), "metrics").get("status"));
  }

  @Test
  void disabledTasksNeverRunButMetricsStillRun() {
    var ops = new RuntimeOperations(new MutableClock(), false);
    var ran = new AtomicInteger();
    ops.run("maintenance", r -> ran.incrementAndGet());
    ops.run("metrics", r -> ran.incrementAndGet());
    assertEquals(1, ran.get());
    assertEquals("DISABLED", job(ops, "maintenance").get("status"));
    assertEquals("OK", job(ops, "metrics").get("status"));
  }

  @Test
  void poisonedFirstPageDoesNotStarveLaterRecordsAndWraps() {
    var ops = new RuntimeOperations(new MutableClock(), true);
    var sweep = new ExpirySweep();
    var attempted = new ArrayList<Long>();
    java.util.function.LongFunction<List<Map<String, Object>>> select =
        cursor ->
            java.util.stream.LongStream.rangeClosed(cursor + 1, 101)
                .limit(100)
                .mapToObj(id -> Map.<String, Object>of("id", id))
                .toList();
    java.util.function.LongConsumer process =
        id -> {
          attempted.add(id);
          if (id <= 100) throw new IllegalStateException("poison");
        };
    ops.run("checkoutExpiry", r -> sweep.run(r, select, process));
    assertEquals(100, attempted.size());
    ops.run("checkoutExpiry", r -> sweep.run(r, select, process));
    assertTrue(attempted.contains(101L));
    ops.run("checkoutExpiry", r -> sweep.run(r, select, process));
    ops.run("checkoutExpiry", r -> sweep.run(r, select, process));
    assertEquals(2, Collections.frequency(attempted, 1L));
  }

  @Test
  void budgetStopsAfterCurrentRecordAndResumesAtNext() {
    var ticks = new AtomicLong();
    var ops = new RuntimeOperations(new MutableClock(), true, ticks::get);
    var sweep = new ExpirySweep();
    var attempted = new ArrayList<Long>();
    java.util.function.LongFunction<List<Map<String, Object>>> select =
        cursor ->
            java.util.stream.LongStream.rangeClosed(cursor + 1, 3)
                .mapToObj(id -> Map.<String, Object>of("id", id))
                .toList();
    ops.run(
        "checkoutExpiry",
        r ->
            sweep.run(
                r,
                select,
                id -> {
                  attempted.add(id);
                  ticks.addAndGet(5_000_000_000L);
                }));
    assertEquals(List.of(1L), attempted);
    ops.run("checkoutExpiry", r -> sweep.run(r, select, attempted::add));
    assertEquals(List.of(1L, 2L, 3L), attempted);
  }
}
