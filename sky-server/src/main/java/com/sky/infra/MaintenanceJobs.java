package com.sky.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final RuntimeOperations operations;
  private final LongSupplier ticker;
  private final AtomicInteger first = new AtomicInteger();
  private final AtomicLong budgetExhausted = new AtomicLong();
  private static final long BUDGET_NANOS = 5_000_000_000L;

  @Autowired
  public MaintenanceJobs(
      Db db,
      Clock clock,
      MeterRegistry metrics,
      @Value("${campus.tasks-enabled:true}") boolean enabled,
      RuntimeOperations operations) {
    this(db, clock, metrics, enabled, operations, System::nanoTime);
  }

  MaintenanceJobs(Db db, Clock clock, MeterRegistry metrics, boolean enabled,
      RuntimeOperations operations, LongSupplier ticker) {
    this.db = db;
    this.clock = clock;
    this.metrics = metrics;
    this.enabled = enabled;
    this.operations = operations;
    this.ticker = ticker;
    metrics.gauge("campus.maintenance.failed", failed);
    metrics.gauge("campus.maintenance.budget.exhausted", budgetExhausted);
  }

  @Scheduled(fixedDelay = 600000, initialDelay = 600000)
  public void work() {
    if (!enabled) return;
    operations.run(
        "maintenance",
        round -> {
          try {
            clean();
            failed.set(0);
          } catch (Exception ex) {
            failed.set(1);
            metrics.counter("campus.maintenance.failures").increment();
            round.failed(ex, "maintenance");
          }
        });
  }

  public void clean() {
    var now = LocalDateTime.now(clock);
    long started = ticker.getAsLong();
    int begin = first.getAndIncrement() & 1;
    boolean[] pending = {true, true};
    budgetExhausted.set(0);
    for (int batch = 0; batch < 10 && (pending[0] || pending[1]); batch++) {
      for (int turn = 0; turn < 2; turn++) {
        int kind = (begin + turn) & 1;
        if (!pending[kind]) continue;
        if (ticker.getAsLong() - started >= BUDGET_NANOS) {
          budgetExhausted.set(1);
          metrics.counter("campus.maintenance.budget.exhaustions").increment();
          return;
        }
        int deleted = kind == 0
            ? db.update("DELETE FROM auth_session WHERE expires_at<? ORDER BY expires_at LIMIT 200",
                now.minusDays(7))
            : db.update("DELETE FROM outbox_event WHERE state='SUCCEEDED' AND created_at<?"
                + " ORDER BY created_at LIMIT 200", now.minusDays(30));
        metrics.counter("campus.maintenance.deleted", "kind", kind == 0 ? "sessions" : "outbox")
            .increment(deleted);
        pending[kind] = deleted == 200;
      }
    }
  }
}
