package com.sky.infra;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RedisAccessTest {
  @Test
  void failureSkipsRepeatedCallsThenPermitsOneRecoveryProbe() throws Exception {
    var now = new AtomicLong(1);
    var metrics = new SimpleMeterRegistry();
    var access = new RedisAccess(now::get, metrics);
    assertThrows(IllegalStateException.class, () -> access.call(() -> { throw new IllegalStateException("down"); }));
    assertTrue(access.isOpen());
    assertThrows(RedisAccess.Unavailable.class, () -> access.call(() -> fail("Must skip the failed dependency")));
    now.addAndGet(TimeUnit.SECONDS.toNanos(5));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var probe = pool.submit(() -> access.call(() -> { entered.countDown(); await(release); return "back"; }));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      try {
        assertThrows(RedisAccess.Unavailable.class, () -> access.call(() -> fail("Only one probe")));
      } finally { release.countDown(); }
      assertEquals("back", probe.get(5, TimeUnit.SECONDS));
      assertFalse(access.isOpen());
      assertEquals("healthy", access.call(() -> "healthy"));
      assertEquals(1, metrics.get("campus.redis.recovery.probes").counter().count());
      assertEquals(1, metrics.get("campus.redis.calls.failed").counter().count());
    }
  }

  @Test
  void activeRedisCommandsAreBoundedAndLateSuccessCannotCloseANewerFailure() throws Exception {
    var access = new RedisAccess(new SimpleMeterRegistry());
    var entered = new CountDownLatch(4);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(4)) {
      var calls = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 4; i++) calls.add(pool.submit(() -> access.call(() -> {
        entered.countDown(); await(release); return 1;
      })));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      try {
        assertThrows(RedisAccess.Unavailable.class, () -> access.call(() -> fail("No fifth active command")));
        assertFalse(access.isOpen());
      } finally { release.countDown(); }
      for (var call : calls) assertEquals(1, call.get(5, TimeUnit.SECONDS));
    }
    var slowEntered = new CountDownLatch(1);
    var slowRelease = new CountDownLatch(1);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var slowSuccess = pool.submit(() -> access.call(() -> { slowEntered.countDown(); await(slowRelease); return 2; }));
      assertTrue(slowEntered.await(5, TimeUnit.SECONDS));
      try {
        assertThrows(IllegalStateException.class, () -> access.call(() -> { throw new IllegalStateException("new failure"); }));
      } finally { slowRelease.countDown(); }
      assertEquals(2, slowSuccess.get(5, TimeUnit.SECONDS));
      assertTrue(access.isOpen());
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
