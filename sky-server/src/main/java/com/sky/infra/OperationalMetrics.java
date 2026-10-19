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
  private final MeterRegistry registry;

  public OperationalMetrics(Db db, StringRedisTemplate redis, MeterRegistry registry) {
    this.db = db;
    this.redis = redis;
    this.registry = registry;
    registry.gauge("campus.redis.degraded", degraded);
    registry.gauge("campus.refund.failed", refundFailures);
    registry.gauge("campus.outbox.backlog", outboxBacklog);
    registry.gauge("campus.metrics.valid", valid);
  }

  @Scheduled(fixedDelay = 10000)
  public void sample() {
    try {
      try (var connection = redis.getConnectionFactory().getConnection()) {
        connection.ping();
      }
      degraded.set(0);
    } catch (Exception ex) {
      degraded.set(1);
    }
    try {
      refundFailures.set(db.count("SELECT COUNT(*) FROM refund_request WHERE state='FAILED'"));
      outboxBacklog.set(db.count("SELECT COUNT(*) FROM outbox_event WHERE state<>'SUCCEEDED'"));
      valid.set(1);
    } catch (Exception ignored) {
      valid.set(0);
      refundFailures.set(-1);
      outboxBacklog.set(-1);
      registry.counter("campus.metrics.failures").increment();
    }
  }
}
