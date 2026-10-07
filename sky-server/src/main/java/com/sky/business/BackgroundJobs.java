package com.sky.business;

import com.sky.infra.Db;
import java.time.*;
import org.slf4j.*;
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
  private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);

  public BackgroundJobs(
      Db db,
      TaskRunner tasks,
      OrderCommandService orders,
      Clock clock,
      @Value("${campus.tasks-enabled:true}") boolean enabled) {
    this.db = db;
    this.tasks = tasks;
    this.orders = orders;
    this.clock = clock;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelay = 2000)
  public void work() {
    if (!enabled) return;
    try {
      for (int i = 0; i < 20 && tasks.runRefund(); i++) {}
      for (int i = 0; i < 50 && tasks.runEvent(); i++) {}
    } catch (Exception ex) {
      log.warn("Background processing temporarily unavailable: {}", ex.getClass().getSimpleName());
    }
  }

  @Scheduled(fixedDelay = 30000)
  public void timeouts() {
    if (!enabled) return;
    scanTimeouts();
  }

  public void scanTimeouts() {
    try {
      for (var row :
          db.rows(
              "SELECT id FROM orders WHERE status=1 AND pay_expires_at<=? ORDER BY id LIMIT 100",
              LocalDateTime.now(clock)))
        try {
          orders.expire(Db.id(row, "id"));
        } catch (Exception ex) {
          log.warn("Expiry processing will retry; order={}", row.get("id"));
        }
      for (var row :
          db.rows(
              "SELECT id FROM orders WHERE status=4 AND delivery_started_at<? AND"
                  + " delivery_overdue=false ORDER BY id LIMIT 100",
              LocalDateTime.now(clock).minusMinutes(60))) orders.markOverdue(Db.id(row, "id"));
    } catch (Exception ex) {
      log.warn("Timeout processing temporarily unavailable");
    }
  }
}
