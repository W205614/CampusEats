package com.sky.business;

import com.sky.infra.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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
  private final Semaphore fallback = new Semaphore(8, true);
  private final ConcurrentHashMap<String, CompletableFuture<List<Map<String, Object>>>> loading =
      new ConcurrentHashMap<>();
  private static final long WAIT_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
  private final ReentrantReadWriteLock[] stripes = new ReentrantReadWriteLock[256];

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
    for (int i = 0; i < stripes.length; i++) stripes[i] = new ReentrantReadWriteLock(true);
  }

  public List<Map<String, Object>> load(String key, Supplier<List<Map<String, Object>>> loader) {
    var lock = stripe(key).readLock();
    long deadline = System.nanoTime() + WAIT_NANOS;
    try {
      if (!lock.tryLock(WAIT_NANOS, TimeUnit.NANOSECONDS)) throw busy();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw busy();
    }
    try {
      var hit = cached(key);
      if (hit != null) return hit;
      var own = new CompletableFuture<List<Map<String, Object>>>();
      var current = loading.putIfAbsent(key, own);
      if (current != null) {
        try {
          return copy(current.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
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
        var rows = cached(key);
        if (rows == null) {
          rows = copy(loader.get());
          try {
            redis.opsForValue().set("campus:" + key, json.write(rows),
                Duration.ofSeconds(rows.isEmpty() ? 30 : 240 + ThreadLocalRandom.current().nextInt(121)));
          } catch (Exception ignored) {
          }
        }
        own.complete(rows);
        return copy(rows);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        var failure = busy();
        own.completeExceptionally(failure);
        throw failure;
      } catch (RuntimeException | Error ex) {
        own.completeExceptionally(ex);
        throw ex;
      } finally {
        loading.remove(key, own);
        if (admitted) fallback.release();
      }
    } finally {
      lock.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> cached(String key) {
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

  /** Persist invalidation with the catalog write; Redis is an after-commit optimization. */
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
              try {
                retry(key);
              } catch (Exception ignored) {
              }
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
    // In one instance, deletion must follow every older in-flight fill, including Outbox retries.
    var lock = stripe(key).writeLock();
    lock.lock();
    try {
      redis.delete("campus:" + key);
    } finally {
      lock.unlock();
    }
  }

  private ReentrantReadWriteLock stripe(String key) {
    return stripes[(key.hashCode() & Integer.MAX_VALUE) % stripes.length];
  }
}
