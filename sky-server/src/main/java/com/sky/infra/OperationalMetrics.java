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

  public OperationalMetrics(Db db, StringRedisTemplate redis, MeterRegistry registry) {
    this.db = db;
    this.redis = redis;
    registry.gauge("campus.redis.degraded", degraded);
    registry.gauge("campus.refund.failed", refundFailures);
    registry.gauge("campus.outbox.backlog", outboxBacklog);
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
      refundFailures.set(db.count("SELECT COUNT(*) FROM refund_task WHERE state='FAILED'"));
      outboxBacklog.set(db.count("SELECT COUNT(*) FROM outbox_event WHERE state<>'SUCCEEDED'"));
    } catch (Exception ignored) {
    }
  }
}
