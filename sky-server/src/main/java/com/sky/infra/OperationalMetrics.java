package com.sky.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {
  private final Db db;
  private final StringRedisTemplate redis;
  private final AtomicLong degraded = new AtomicLong(),
      refundFailures = new AtomicLong(),
      outboxBacklog = new AtomicLong();
  private final AtomicLong valid = new AtomicLong();
  private final AtomicLong evictedKeys = new AtomicLong(-1);
  private final AtomicLong openExceptions = new AtomicLong(-1),
      oldestRefund = new AtomicLong(-1),
      oldestOutbox = new AtomicLong(-1),
      uploadBytes = new AtomicLong(-1);
  private final java.nio.file.Path uploads;
  private long nextDiskSample;
  private final MeterRegistry registry;
  private final RuntimeOperations operations;

  public OperationalMetrics(
      Db db, StringRedisTemplate redis, MeterRegistry registry, RuntimeOperations operations) {
    this(db, redis, registry, operations, ".local/uploads");
  }

  @org.springframework.beans.factory.annotation.Autowired
  public OperationalMetrics(
      Db db,
      StringRedisTemplate redis,
      MeterRegistry registry,
      RuntimeOperations operations,
      @org.springframework.beans.factory.annotation.Value("${campus.uploads}") String uploads) {
    this.db = db;
    this.redis = redis;
    this.registry = registry;
    this.operations = operations;
    this.uploads = java.nio.file.Path.of(uploads);
    registry.gauge("campus.redis.degraded", degraded);
    registry.gauge("campus.refund.failed", refundFailures);
    registry.gauge("campus.outbox.backlog", outboxBacklog);
    registry.gauge("campus.metrics.valid", valid);
    registry.gauge("campus.redis.evicted.keys", evictedKeys);
    registry.gauge("campus.order.exceptions.open", openExceptions);
    registry.gauge("campus.refund.oldest.wait.seconds", oldestRefund);
    registry.gauge("campus.outbox.oldest.wait.seconds", oldestOutbox);
    registry.gauge("campus.upload.bytes", uploadBytes);
  }

  @Scheduled(fixedDelay = 10000)
  public void sample() {
    operations.run("metrics", this::sampleRound);
  }

  private void sampleRound(RuntimeOperations.Round round) {
    try {
      try (var connection = redis.getConnectionFactory().getConnection()) {
        connection.ping();
        try {
          var stats = connection.serverCommands().info("stats");
          evictedKeys.set(
              stats == null ? -1 : Long.parseLong(stats.getProperty("evicted_keys", "-1")));
        } catch (Exception unavailableStats) {
          evictedKeys.set(-1);
        }
      }
      degraded.set(0);
    } catch (Exception ex) {
      degraded.set(1);
      evictedKeys.set(-1);
    }
    try {
      refundFailures.set(db.count("SELECT COUNT(*) FROM refund_request WHERE state='FAILED'"));
      outboxBacklog.set(db.count("SELECT COUNT(*) FROM outbox_event WHERE state<>'SUCCEEDED'"));
      openExceptions.set(db.count("SELECT COUNT(*) FROM order_exception WHERE state<>'RESOLVED'"));
      oldestRefund.set(age("refund_request"));
      oldestOutbox.set(age("outbox_event"));
      if (System.nanoTime() >= nextDiskSample) {
        nextDiskSample = System.nanoTime() + java.time.Duration.ofMinutes(5).toNanos();
        if (java.nio.file.Files.exists(uploads))
          try (var paths = java.nio.file.Files.walk(uploads)) {
            uploadBytes.set(
                paths
                    .filter(java.nio.file.Files::isRegularFile)
                    .mapToLong(
                        p -> {
                          try {
                            return java.nio.file.Files.size(p);
                          } catch (java.io.IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                          }
                        })
                    .sum());
          } catch (Exception ex) {
            uploadBytes.set(-1);
          }
      }
      valid.set(1);
    } catch (Exception ex) {
      valid.set(0);
      refundFailures.set(-1);
      outboxBacklog.set(-1);
      registry.counter("campus.metrics.failures").increment();
      round.failed(ex, "metrics");
    }
  }

  private long age(String table) {
    var r =
        db.one(
            "SELECT COALESCE(TIMESTAMPDIFF(SECOND,MIN(created_at),CURRENT_TIMESTAMP),0) age FROM "
                + table
                + " WHERE state IN('REQUESTED','PROCESSING')");
    return r == null ? -1 : Math.max(0, Db.id(r, "age"));
  }
}
