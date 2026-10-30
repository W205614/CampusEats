package com.sky.business;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sky.infra.Db;
import com.sky.infra.Json;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class MenuCacheTest {
  @ParameterizedTest
  @ValueSource(strings = {"missing", "null", "string", "object", "null-element", "non-map-element", "empty"})
  @SuppressWarnings("unchecked")
  void malformedItemsReloadFromSourceWhileAnEmptyListRemainsACacheHit(String shape) {
    var stored = new ConcurrentHashMap<String, String>();
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
    doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(1)); return null; })
        .when(values).set(anyString(), anyString(), any(Duration.class));
    var json = new Json(new ObjectMapper());
    var cache = new MenuCache(redis, json, mock(Db.class), Clock.systemUTC(),
        mock(PlatformTransactionManager.class));
    cache.load("shape", List::of);
    var envelope = json.map(stored.get("campus:shape"));
    switch (shape) {
      case "missing" -> envelope.remove("items");
      case "null" -> envelope.put("items", null);
      case "string" -> envelope.put("items", "invalid");
      case "object" -> envelope.put("items", Map.of());
      case "null-element" -> envelope.put("items", Collections.singletonList(null));
      case "non-map-element" -> envelope.put("items", List.of("invalid"));
      case "empty" -> envelope.put("items", List.of());
    }
    stored.put("campus:shape", json.write(envelope));
    var reads = new AtomicInteger();
    var expected = shape.equals("empty") ? List.<Map<String, Object>>of()
        : List.<Map<String, Object>>of(Map.of("name", "current menu"));
    assertEquals(expected, cache.load("shape", () -> {
      reads.incrementAndGet();
      return expected;
    }));
    assertEquals(shape.equals("empty") ? 0 : 1, reads.get());
    assertEquals(expected, cache.load("shape", () -> fail("Expected repaired cache hit")));
  }

  @Test
  @SuppressWarnings("unchecked")
  void concurrentMissesShareOneFillAndNeverShareMutableResults() throws Exception {
    var stored = new ConcurrentHashMap<String, String>();
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
    doAnswer(call -> {
      stored.put(call.getArgument(0), call.getArgument(1));
      return null;
    }).when(values).set(anyString(), anyString(), any(Duration.class));
    var cache = new MenuCache(redis, new Json(new ObjectMapper()), mock(Db.class),
        Clock.systemUTC(), mock(PlatformTransactionManager.class));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    // Stay within Redis admission; bypass behavior is covered by the Redis failure test.
    var attempts = new CountDownLatch(4);
    var count = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(4)) {
      var results = new ArrayList<Future<List<Map<String, Object>>>>();
      for (int i = 0; i < 4; i++) results.add(pool.submit(() -> {
        attempts.countDown();
        return cache.load("shared", () -> {
          count.incrementAndGet();
          entered.countDown();
          await(release);
          return List.of(Map.of("price", "10.00", "flavors", List.of(new HashMap<>(Map.of("name", "辣度")))));
        });
      }));
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertTrue(attempts.await(5, TimeUnit.SECONDS));
      } finally {
        release.countDown();
      }
      var first = results.getFirst().get(5, TimeUnit.SECONDS);
      first.getFirst().put("price", "changed");
      ((Map<String, Object>) ((List<?>) first.getFirst().get("flavors")).getFirst()).put("name", "changed");
      for (int i = 1; i < results.size(); i++) {
        var other = results.get(i).get(5, TimeUnit.SECONDS);
        assertEquals("10.00", other.getFirst().get("price"));
        assertEquals("辣度", ((Map<?, ?>) ((List<?>) other.getFirst().get("flavors")).getFirst()).get("name"));
      }
      assertEquals(1, count.get());
      assertEquals("10.00", cache.load("shared", () -> fail("Expected a cache hit")).getFirst().get("price"));
    }
  }

  @Test
  void ninthDistinctFillWaitsAtMostTheAdmissionBudgetAndFailureReleasesSlot() throws Exception {
    var redis = mock(StringRedisTemplate.class);
    var values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    var cache = new MenuCache(redis, new Json(new ObjectMapper()), mock(Db.class),
        Clock.systemUTC(), mock(PlatformTransactionManager.class));
    var entered = new CountDownLatch(8);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(9)) {
      var loaders = new ArrayList<Future<?>>();
      for (int i = 0; i < 8; i++) {
        String key = "held-" + i;
        loaders.add(pool.submit(() -> cache.load(key, () -> {
          entered.countDown();
          await(release);
          return List.of();
        })));
      }
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var rejected = pool.submit(() -> assertThrows(com.sky.common.BusinessException.class,
            () -> cache.load("ninth", () -> fail("No ninth backfill may be admitted"))));
        assertEquals("MENU_BUSY", rejected.get(4, TimeUnit.SECONDS).code());
      } finally {
        release.countDown();
      }
      for (var loader : loaders) loader.get(5, TimeUnit.SECONDS);
      assertThrows(IllegalStateException.class, () -> cache.load("failed", () -> {
        throw new IllegalStateException("loader failed");
      }));
      assertEquals("recovered", cache.load("failed",
          () -> List.of(Map.<String, Object>of("state", "recovered"))).getFirst().get("state"));
    }
  }

  @Test
  void redisFailureStillCoalescesInFlightLoads() throws Exception {
    var redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenThrow(new IllegalStateException("unavailable"));
    var cache = new MenuCache(redis, new Json(new ObjectMapper()), mock(Db.class),
        Clock.systemUTC(), mock(PlatformTransactionManager.class));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> cache.load("degraded", () -> {
        entered.countDown();
        await(release);
        return List.of(Map.<String, Object>of("state", "fallback"));
      }));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      var second = pool.submit(() -> assertThrows(com.sky.common.BusinessException.class,
          () -> cache.load("degraded", () -> fail("Must join the in-flight loader"))));
      try {
        assertEquals("MENU_BUSY", second.get(4, TimeUnit.SECONDS).code());
      } finally {
        release.countDown();
      }
      assertEquals("fallback", first.get(5, TimeUnit.SECONDS).getFirst().get("state"));
    }
  }

  private static void await(CountDownLatch release) {
    try {
      if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"immediate", "afterCommit", "retry"})
  @SuppressWarnings("unchecked")
  void oldFillCannotSurviveCompletedInvalidation(String path) throws Exception {
    var stored = new ConcurrentHashMap<String, String>();
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
    doAnswer(call -> {
      stored.put(call.getArgument(0), call.getArgument(1));
      return null;
    }).when(values).set(anyString(), anyString(), any(Duration.class));
    when(redis.delete(anyString())).thenAnswer(call -> stored.remove(call.getArgument(0)) != null);
    var cache = new MenuCache(redis, new Json(new ObjectMapper()), mock(Db.class),
        Clock.systemUTC(), mock(PlatformTransactionManager.class));
    var oldRead = new CountDownLatch(1);
    var finishRead = new CountDownLatch(1);
    var invalidating = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    String key = "menu:DISH:1";
    try {
      var loading = pool.submit(() -> cache.load(key, () -> {
        var oldRows = List.<Map<String, Object>>of(Map.of("price", "10.00"));
        oldRead.countDown();
        try {
          assertTrue(finishRead.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(ex);
        }
        return oldRows;
      }));
      assertTrue(oldRead.await(5, TimeUnit.SECONDS));
      var invalidation = pool.submit(() -> {
        invalidating.countDown();
        switch (path) {
          case "immediate" -> cache.invalidate(key);
          case "retry" -> cache.retry(key);
          case "afterCommit" -> {
            TransactionSynchronizationManager.initSynchronization();
            try {
              cache.afterCommit(key);
              for (var sync : TransactionSynchronizationManager.getSynchronizations())
                sync.afterCommit();
            } finally {
              TransactionSynchronizationManager.clearSynchronization();
            }
          }
          default -> throw new IllegalArgumentException(path);
        }
      });
      assertTrue(invalidating.await(5, TimeUnit.SECONDS));
      invalidation.get(1, TimeUnit.SECONDS);
      // A new generation must neither wait for nor reuse an older in-flight load.
      assertEquals("20.00", cache.load(key,
          () -> List.<Map<String, Object>>of(Map.of("price", "20.00"))).getFirst().get("price"));
      finishRead.countDown();
      assertEquals("10.00", loading.get(5, TimeUnit.SECONDS).getFirst().get("price"));
      invalidation.get(5, TimeUnit.SECONDS);
      assertEquals("20.00", cache.load(key,
          () -> List.<Map<String, Object>>of(Map.of("price", "20.00"))).getFirst().get("price"));
    } finally {
      finishRead.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void afterCommitNeverPerformsRedisIoAndNewProcessRejectsAnOldEnvelope() {
    var stored = new ConcurrentHashMap<String, String>();
    var redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
    doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(1)); return null; })
        .when(values).set(anyString(), anyString(), any(Duration.class));
    var db = mock(Db.class);
    var cache = new MenuCache(redis, new Json(new ObjectMapper()), db,
        Clock.systemUTC(), mock(PlatformTransactionManager.class));
    cache.load("process", () -> List.of(Map.of("value", "old")));
    var restarted = new MenuCache(redis, new Json(new ObjectMapper()), db,
        Clock.systemUTC(), mock(PlatformTransactionManager.class));
    assertEquals("new", restarted.load("process", () -> List.of(Map.of("value", "new")))
        .getFirst().get("value"));
    clearInvocations(redis, values);
    TransactionSynchronizationManager.initSynchronization();
    try {
      restarted.afterCommit("process");
      for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCommit();
      verifyNoInteractions(redis, values);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
    assertEquals("committed", restarted.load("process", () -> List.of(Map.of("value", "committed")))
        .getFirst().get("value"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void failedRateCommandAlsoShortCircuitsMenuRedisCalls() {
    var redis = mock(StringRedisTemplate.class);
    doThrow(new IllegalStateException("down")).when(redis).execute(
        any(org.springframework.data.redis.core.script.RedisScript.class), anyList(), any(Object[].class));
    var access = new com.sky.infra.RedisAccess();
    var rate = new com.sky.security.RateLimits(redis, Clock.systemUTC(), access);
    rate.check("public", 10, 60);
    var cache = new MenuCache(redis, new Json(new ObjectMapper()), mock(Db.class),
        Clock.systemUTC(), mock(PlatformTransactionManager.class), access);
    assertEquals("available", cache.load("menu", () -> List.of(Map.of("value", "available")))
        .getFirst().get("value"));
    verify(redis, never()).opsForValue();
  }
}
