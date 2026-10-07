package com.sky.business;

import com.sky.infra.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;

@Service
public class MenuCache {
  private final StringRedisTemplate redis;
  private final Json json;
  private final Db db;
  private final Clock clock;
  private final TransactionTemplate retryTx;
  private final Semaphore fallback = new Semaphore(20);

  public MenuCache(
      StringRedisTemplate redis,
      Json json,
      Db db,
      Clock clock,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this.retryTx = new TransactionTemplate(manager);
    this.retryTx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.redis = redis;
    this.json = json;
    this.db = db;
    this.clock = clock;
  }

  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> load(String key, Supplier<List<Map<String, Object>>> loader) {
    try {
      String hit = redis.opsForValue().get("campus:" + key);
      if (hit != null) {
        var out = new ArrayList<Map<String, Object>>();
        for (var item :
            json.map("{\"items\":" + hit + "}").get("items") instanceof List<?> l ? l : List.of())
          out.add((Map<String, Object>) item);
        return out;
      }
    } catch (Exception ignored) {
    }
    com.sky.common.BusinessException.require(fallback.tryAcquire(), 503, "MENU_BUSY", "菜单繁忙，请稍后重试");
    try {
      var rows = loader.get();
      try {
        redis
            .opsForValue()
            .set(
                "campus:" + key,
                json.write(rows),
                Duration.ofSeconds(
                    rows.isEmpty() ? 30 : 240 + ThreadLocalRandom.current().nextInt(121)));
      } catch (Exception ignored) {
      }
      return rows;
    } finally {
      fallback.release();
    }
  }

  /** Persist invalidation with the catalog write; Redis is an after-commit optimization. */
  public void afterCommit(String key) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      LocalDateTime now = LocalDateTime.now(clock);
      db.update(
          "INSERT INTO outbox_event(event_type,aggregate_id,payload,next_attempt_at,created_at)"
              + " VALUES('CACHE_INVALIDATE',0,?,?,?)",
          json.write(Map.of("key", key)),
          now,
          now);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              try {
                redis.delete("campus:" + key);
              } catch (Exception ignored) {
              }
            }
          });
    } else invalidate(key);
  }

  public void invalidate(String key) {
    try {
      redis.delete("campus:" + key);
    } catch (Exception ex) {
      LocalDateTime now = LocalDateTime.now(clock);
      retryTx.executeWithoutResult(
          status ->
              db.update(
                  "INSERT INTO"
                      + " outbox_event(event_type,aggregate_id,payload,next_attempt_at,created_at)"
                      + " VALUES('CACHE_INVALIDATE',0,?,?,?)",
                  json.write(Map.of("key", key)),
                  now,
                  now));
    }
  }

  public void retry(String key) {
    redis.delete("campus:" + key);
  }
}
