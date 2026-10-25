package com.sky.business;

import com.sky.infra.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLongArray;
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
  private final RedisAccess access;
  private final Semaphore fallback = new Semaphore(8, true);
  private record Loading(long generation, CompletableFuture<List<Map<String, Object>>> result) {}
  private final ConcurrentHashMap<String, Loading> loading =
      new ConcurrentHashMap<>();
  private static final long WAIT_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
  private final AtomicLongArray generations = new AtomicLongArray(256);
  private final String epoch = UUID.randomUUID().toString();

  public MenuCache(
      StringRedisTemplate redis,
      Json json,
      Db db,
      Clock clock,
      org.springframework.transaction.PlatformTransactionManager manager) {
    this(redis, json, db, clock, manager, new RedisAccess());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public MenuCache(
      StringRedisTemplate redis, Json json, Db db, Clock clock,
      org.springframework.transaction.PlatformTransactionManager manager, RedisAccess access) {
    this.retryTx = new TransactionTemplate(manager);
    this.retryTx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.redis = redis;
    this.json = json;
    this.db = db;
    this.clock = clock;
    this.access = access;
  }

  public List<Map<String, Object>> load(String key, Supplier<List<Map<String, Object>>> loader) {
    long generation = generations.get(stripe(key));
    long deadline = System.nanoTime() + WAIT_NANOS;
    var hit = cached(key, generation);
    if (hit != null) return hit;
    var own = new Loading(generation, new CompletableFuture<>());
    Loading current;
    while ((current = loading.putIfAbsent(key, own)) != null && current.generation() < generation)
      loading.remove(key, current);
    if (current != null) {
      try {
        return copy(current.result().get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw busy();
      } catch (TimeoutException ex) {
        throw busy();
      } catch (ExecutionException ex) {
        if (ex.getCause() instanceof RuntimeException cause) throw cause;
        if (ex.getCause() instanceof Error cause) throw cause;
        throw new IllegalStateException(ex.getCause());
      }
    }
    boolean admitted = false;
    try {
      admitted = fallback.tryAcquire(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
      if (!admitted) throw busy();
      // Recheck after admission: another fill can finish between the initial GET and registration.
      var rows = cached(key, generation);
      if (rows == null) {
        rows = copy(loader.get());
        try {
          var snapshot = rows;
          access.call(() -> {
            redis.opsForValue().set("campus:" + key,
                json.write(Map.of("epoch", epoch, "generation", generation, "items", snapshot)),
                Duration.ofSeconds(snapshot.isEmpty() ? 30 : 240 + ThreadLocalRandom.current().nextInt(121)));
            return true;
          });
        } catch (Exception ignored) {
        }
      }
      own.result().complete(rows);
      return copy(rows);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      var failure = busy();
      own.result().completeExceptionally(failure);
      throw failure;
    } catch (RuntimeException | Error ex) {
      own.result().completeExceptionally(ex);
      throw ex;
    } finally {
      loading.remove(key, own);
      if (admitted) fallback.release();
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> cached(String key, long generation) {
    try {
      String hit = access.call(() -> redis.opsForValue().get("campus:" + key));
      if (hit != null) {
        var envelope = json.map(hit);
        if (!epoch.equals(envelope.get("epoch"))
            || !(envelope.get("generation") instanceof Number value)
            || value.longValue() != generation) return null;
        var out = new ArrayList<Map<String, Object>>();
        for (var item :
            envelope.get("items") instanceof List<?> l ? l : List.of())
          out.add((Map<String, Object>) item);
        return out;
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static com.sky.common.BusinessException busy() {
    return new com.sky.common.BusinessException(503, "MENU_BUSY", "菜单繁忙，请稍后重试");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> copy(List<Map<String, Object>> rows) {
    return (List<Map<String, Object>>) copyValue(rows);
  }

  private static Object copyValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      var result = new LinkedHashMap<String, Object>();
      map.forEach((key, item) -> result.put(String.valueOf(key), copyValue(item)));
      return result;
    }
    if (value instanceof Collection<?> values) {
      var result = new ArrayList<Object>();
      values.forEach(item -> result.add(copyValue(item)));
      return result;
    }
    if (value instanceof byte[] bytes) return bytes.clone();
    if (value instanceof java.sql.Timestamp timestamp) return timestamp.clone();
    if (value instanceof java.util.Date date) return date.clone();
    return value;
  }

  /** Commit invalidation durably; the local generation changes without waiting on reads or Redis. */
  public void afterCommit(String key) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      LocalDateTime now = LocalDateTime.now(clock);
      Long storeId = key.matches("store:[0-9]+:catalog") ? Long.parseLong(key.split(":")[1]) : null;
      db.update(
          "INSERT INTO"
              + " outbox_event(event_type,aggregate_id,payload,next_attempt_at,created_at,store_id,campus_id)"
              + " VALUES('CACHE_INVALIDATE',0,?,?,?,?,(SELECT campus_id FROM store WHERE id=?))",
          json.write(Map.of("key", key)),
          now,
          now,
          storeId,
          storeId);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              // No Redis I/O or reader lock on the committed write's response path.
              // The durable event removes the entry later; generations make old fills unreadable now.
              advance(key);
            }
          });
    } else invalidate(key);
  }

  public void invalidate(String key) {
    try {
      retry(key);
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
    advance(key);
    access.call(() -> redis.delete("campus:" + key));
  }

  private void advance(String key) {
    generations.incrementAndGet(stripe(key));
  }

  private int stripe(String key) {
    return (key.hashCode() & Integer.MAX_VALUE) % generations.length();
  }
}
