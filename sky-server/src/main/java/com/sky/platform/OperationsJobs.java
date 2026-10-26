package com.sky.platform;

import com.sky.infra.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OperationsJobs {
  private final Db db;
  private final Clock clock;
  private final OperationsService service;
  private final RuntimeOperations operations;
  private final boolean enabled;
  private final ExpirySweep sweep = new ExpirySweep();

  public OperationsJobs(
      Db db,
      Clock clock,
      OperationsService service,
      RuntimeOperations operations,
      @Value("${campus.tasks-enabled:true}") boolean enabled) {
    this.db = db;
    this.clock = clock;
    this.service = service;
    this.operations = operations;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelay = 10000)
  public void work() {
    if (!enabled) return;
    operations.run(
        "fulfillmentExceptions",
        round ->
            sweep.run(
                round,
                cursor ->
                    db.rows(
                        "SELECT o.id FROM orders o JOIN order_stage s ON s.order_id=o.id LEFT JOIN"
                            + " employee p ON p.id=o.courier_id WHERE o.id>? AND o.status IN(2,3,4)"
                            + " AND (s.deadline_at<=? OR (o.courier_id IS NOT NULL AND (p.id IS"
                            + " NULL OR p.status<>1 OR p.role<>'DELIVERER' OR"
                            + " p.campus_id<>o.campus_id))) ORDER BY o.id LIMIT 200",
                        cursor,
                        LocalDateTime.now(clock)),
                service::detect));
  }
}
