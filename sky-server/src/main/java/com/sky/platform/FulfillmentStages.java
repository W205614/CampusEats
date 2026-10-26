package com.sky.platform;

import com.sky.infra.Db;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Called inside the existing order transaction; stage deadlines are immutable snapshots. */
@Component
public class FulfillmentStages {
  private final Db db;
  private final Clock clock;
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  public FulfillmentStages(
      Db db, Clock clock, io.micrometer.core.instrument.MeterRegistry metrics) {
    this.db = db;
    this.clock = clock;
    this.metrics = metrics;
  }

  public void sync(long order) {
    var o =
        db.one(
            "SELECT o.*,t.state task_state FROM orders o LEFT JOIN delivery_task t ON"
                + " t.order_id=o.id WHERE o.id=?",
            order);
    if (o == null) return;
    String stage = stage(o);
    var old = db.one("SELECT * FROM order_stage WHERE order_id=? FOR UPDATE", order);
    if (old == null || !stage.equals(old.get("stage"))) {
      var at = LocalDateTime.now(clock);
      if (old != null)
        io.micrometer.core.instrument.Timer.builder("campus.fulfillment.stage.duration")
            .tag("stage", old.get("stage").toString())
            .register(metrics)
            .record(
                Duration.ofMillis(
                    Math.max(0, Duration.between(Db.time(old.get("started_at")), at).toMillis())));
      db.update("INSERT IGNORE INTO service_policy(store_id) VALUES(?)", o.get("store_id"));
      var policy = db.one("SELECT * FROM service_policy WHERE store_id=?", o.get("store_id"));
      String field =
          switch (stage) {
            case "WAIT_ACCEPT" -> "accept_minutes";
            case "PREPARING" -> "prepare_minutes";
            case "WAIT_DISPATCH" -> "dispatch_minutes";
            case "WAIT_PICKUP" -> "pickup_minutes";
            case "DELIVERING" -> "delivery_minutes";
            case "SELF_PICKUP" -> "self_pickup_minutes";
            default -> null;
          };
      long sequence = old == null ? 1 : Db.id(old, "stage_seq") + 1;
      db.update(
          "INSERT INTO order_stage(order_id,stage,stage_seq,started_at,deadline_at)"
              + " VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE"
              + " stage=VALUES(stage),stage_seq=VALUES(stage_seq),started_at=VALUES(started_at),deadline_at=VALUES(deadline_at),migrated=false",
          order,
          stage,
          sequence,
          at,
          field == null ? null : at.plusMinutes(Db.integer(policy, field)));
      db.update(
          "UPDATE order_exception SET state='RESOLVED',resolved_at=?,version=version+1 WHERE"
              + " order_id=? AND state<>'RESOLVED' AND kind='STAGE_OVERDUE'",
          at,
          order);
    }
    db.update(
        "UPDATE order_exception e SET state='RESOLVED',resolved_at=?,version=version+1 WHERE"
            + " e.order_id=? AND e.state<>'RESOLVED' AND e.kind='COURIER_UNAVAILABLE' AND"
            + " (EXISTS(SELECT 1 FROM orders o WHERE o.id=e.order_id AND (o.status NOT IN(3,4) OR"
            + " o.courier_id IS NULL)) OR EXISTS(SELECT 1 FROM orders o JOIN employee p ON"
            + " p.id=o.courier_id WHERE o.id=e.order_id AND p.status=1 AND p.role='DELIVERER' AND"
            + " p.campus_id=o.campus_id))",
        LocalDateTime.now(clock),
        order);
  }

  private String stage(Map<String, Object> o) {
    return switch (Db.integer(o, "status")) {
      case 2 -> "WAIT_ACCEPT";
      case 3 ->
          !Db.bool(o, "ready")
              ? "PREPARING"
              : "PICKUP".equals(o.get("fulfillment"))
                  ? "SELF_PICKUP"
                  : "ASSIGNED".equals(o.get("task_state")) ? "WAIT_PICKUP" : "WAIT_DISPATCH";
      case 4 -> "DELIVERING";
      default -> "FINISHED";
    };
  }
}
