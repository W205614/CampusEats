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
  private final MeterRegistry registry;
  private final RuntimeOperations operations;

  public OperationalMetrics(
      Db db, StringRedisTemplate redis, MeterRegistry registry, RuntimeOperations operations) {
    this.db = db;
    this.redis = redis;
    this.registry = registry;
    this.operations = operations;
    registry.gauge("campus.redis.degraded", degraded);
    registry.gauge("campus.refund.failed", refundFailures);
    registry.gauge("campus.outbox.backlog", outboxBacklog);
    registry.gauge("campus.metrics.valid", valid);
    registry.gauge("campus.redis.evicted.keys", evictedKeys);
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
          evictedKeys.set(stats == null ? -1 : Long.parseLong(stats.getProperty("evicted_keys", "-1")));
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
      valid.set(1);
    } catch (Exception ex) {
      valid.set(0);
      refundFailures.set(-1);
      outboxBacklog.set(-1);
      registry.counter("campus.metrics.failures").increment();
      round.failed(ex, "metrics");
    }
  }
}
