package com.sky.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceJobs {
  private final Db db;
  private final Clock clock;
  private final boolean enabled;
  private final MeterRegistry metrics;
  private final AtomicLong failed = new AtomicLong();

  public MaintenanceJobs(
      Db db,
      Clock clock,
      MeterRegistry metrics,
      @Value("${campus.tasks-enabled:true}") boolean enabled) {
    this.db = db;
    this.clock = clock;
    this.metrics = metrics;
    this.enabled = enabled;
    metrics.gauge("campus.maintenance.failed", failed);
  }

  @Scheduled(fixedDelay = 600000, initialDelay = 600000)
  public void work() {
    if (!enabled) return;
    try {
      clean();
      failed.set(0);
    } catch (Exception ex) {
      failed.set(1);
      metrics.counter("campus.maintenance.failures").increment();
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn("Maintenance failed: {}", ex.getClass().getSimpleName());
    }
  }

  public void clean() {
    var now = LocalDateTime.now(clock);
    db.update(
        "DELETE FROM auth_session WHERE expires_at<? ORDER BY expires_at LIMIT 200",
        now.minusDays(7));
    db.update(
        "DELETE FROM outbox_event WHERE state='SUCCEEDED' AND created_at<? ORDER BY created_at"
            + " LIMIT 200",
        now.minusDays(30));
  }
}
