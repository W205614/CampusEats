package com.sky.business;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sky.infra.Db;
import com.sky.infra.Json;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

class MenuCacheTest {
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
      assertThrows(TimeoutException.class, () -> invalidation.get(150, TimeUnit.MILLISECONDS),
          "Invalidation must wait for the in-flight fill before removing it");
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
}
