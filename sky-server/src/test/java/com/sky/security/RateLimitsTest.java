package com.sky.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sky.common.BusinessException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RateLimitsTest {
  static class MutableClock extends Clock {
    final AtomicLong now = new AtomicLong(Instant.parse("2026-10-07T04:00:00Z").toEpochMilli());
    public ZoneId getZone() { return ZoneOffset.UTC; }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    void advanceSeconds(int seconds) { now.addAndGet(seconds * 1000L); }
  }

  final MutableClock clock = new MutableClock();
  final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  final RateLimits limits = new RateLimits(redis, clock);

  @BeforeEach
  @SuppressWarnings("unchecked")
  void offline() {
    doThrow(new RedisConnectionFailureException("isolated test outage"))
        .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
  }

  void fill(int count) {
    for (int i = 0; i < count; i++) limits.check("existing:" + i, 2, 60);
  }

  void rejects(String code, Runnable call) {
    var error = assertThrows(BusinessException.class, call::run);
    assertEquals(code, error.code());
    assertEquals(code.equals("RATE_LIMITED") ? 429 : 503, error.status());
  }

  @Test
  void fullTableRejectsNewKeysButKeepsExistingLimits() {
    fill(10000);
    rejects("RATE_LIMIT_BUSY", () -> limits.check("new", 2, 60));
    limits.check("existing:0", 2, 60);
    rejects("RATE_LIMITED", () -> limits.check("existing:0", 2, 60));
  }

  @Test
  void fullTableReclaimsExpiredKeysUsingTheirOwnLifetimes() {
    for (int i = 0; i < 100; i++) limits.check("long:" + i, 1, 120);
    for (int i = 0; i < 9900; i++) limits.check("short:" + i, 1, 60);
    clock.advanceSeconds(60);
    limits.check("new", 1, 60);
    rejects("RATE_LIMITED", () -> limits.check("long:0", 1, 120));
    limits.check("short:0", 1, 60);
    clock.advanceSeconds(60);
    limits.check("long:0", 1, 120);
  }

  @Test
  void concurrentAdmissionDoesNotExceedCapacity() throws Exception {
    fill(9990);
    var results = compete(i -> "new:" + i, 2);
    assertEquals(10, results.stream().filter("OK"::equals).count());
    assertEquals(10, results.stream().filter("RATE_LIMIT_BUSY"::equals).count());
    rejects("RATE_LIMIT_BUSY", () -> limits.check("one-more", 2, 60));
  }

  @Test
  void concurrentRequestsCannotLoseIncrements() throws Exception {
    var results = compete(i -> "same-key", 5);
    assertEquals(5, results.stream().filter("OK"::equals).count());
    assertEquals(15, results.stream().filter("RATE_LIMITED"::equals).count());
  }

  @Test
  @SuppressWarnings("unchecked")
  void redisRecoveryBypassesFullFallbackAndOutageCanResume() {
    fill(10000);
    doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    limits.check("recovered", 1, 60);
    offline();
    clock.advanceSeconds(60);
    limits.check("resumed", 1, 60);
    rejects("RATE_LIMITED", () -> limits.check("resumed", 1, 60));
  }

  List<String> compete(java.util.function.IntFunction<String> key, int max) throws Exception {
    var pool = Executors.newFixedThreadPool(20);
    var start = new CountDownLatch(1);
    try {
      var futures = new ArrayList<Future<String>>();
      for (int i = 0; i < 20; i++) {
        int index = i;
        futures.add(pool.submit(() -> {
          start.await();
          try {
            limits.check(key.apply(index), max, 60);
            return "OK";
          } catch (BusinessException ex) {
            return ex.code();
          }
        }));
      }
      start.countDown();
      var results = new ArrayList<String>();
      for (var future : futures) results.add(future.get(10, TimeUnit.SECONDS));
      return results;
    } finally {
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }
  }
}
