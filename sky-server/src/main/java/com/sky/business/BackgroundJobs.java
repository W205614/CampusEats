package com.sky.business;

import com.sky.infra.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackgroundJobs {
  private final Db db;
  private final TaskRunner tasks;
  private final OrderCommandService orders;
  private final Clock clock;
  private final boolean enabled;
  private final RuntimeOperations operations;
  private final ExpirySweep expiry = new ExpirySweep();

  public BackgroundJobs(
      Db db,
      TaskRunner tasks,
      OrderCommandService orders,
      Clock clock,
      @Value("${campus.tasks-enabled:true}") boolean enabled,
      RuntimeOperations operations) {
    this.db = db;
    this.tasks = tasks;
    this.orders = orders;
    this.clock = clock;
    this.enabled = enabled;
    this.operations = operations;
  }

  @Scheduled(fixedDelay = 2000)
  public void work() {
    if (!enabled) return;
    operations.run(
        "legacyRefunds",
        round -> {
          for (int i = 0; i < 20 && round.withinBudget() && tasks.runRefund(round::failed); i++) {}
        });
  }

  @Scheduled(fixedDelay = 2000)
  public void notifications() {
    if (!enabled) return;
    operations.run(
        "outbox",
        round -> {
          for (int i = 0; i < 50 && round.withinBudget() && tasks.runEvent(round::failed); i++) {}
        });
  }

  @Scheduled(fixedDelay = 30000)
  public void timeouts() {
    if (!enabled) return;
    scanTimeouts();
  }

  public void scanTimeouts() {
    operations.run(
        "legacyExpiry",
        round ->
            expiry.run(
                round,
                cursor ->
                    db.rows(
                        "SELECT id FROM orders WHERE checkout_id IS NULL AND status=1"
                            + " AND pay_expires_at<=? AND id>? ORDER BY id LIMIT 100",
                        LocalDateTime.now(clock),
                        cursor),
                orders::expire));
  }
}
