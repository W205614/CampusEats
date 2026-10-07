package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.infra.*;
import com.sky.security.Actor;
import com.sky.websocket.OrderSocket;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskRunner {
  private final Db db;
  private final Clock clock;
  private final Json json;
  private final MockPaymentGateway gateway;
  private final OrderSocket socket;
  private final MenuCache cache;
  private final TransactionTemplate tx;
  private final Audit audit;
  private final OrderEvents events;

  public TaskRunner(
      Db db,
      Clock clock,
      Json json,
      MockPaymentGateway gateway,
      OrderSocket socket,
      MenuCache cache,
      PlatformTransactionManager manager,
      Audit audit,
      OrderEvents events) {
    this.events = events;
    this.db = db;
    this.clock = clock;
    this.json = json;
    this.gateway = gateway;
    this.socket = socket;
    this.cache = cache;
    this.tx = new TransactionTemplate(manager);
    this.audit = audit;
  }

  public boolean runRefund() {
    var task = claim("refund_task");
    if (task == null) return false;
    try {
      gateway.refund(Db.id(task, "order_id"));
      tx.executeWithoutResult(
          s -> {
            var current =
                db.one(
                    "SELECT * FROM refund_task WHERE id=? AND lease_token=? AND state='PROCESSING'"
                        + " FOR UPDATE",
                    task.get("id"),
                    task.get("lease_token"));
            if (current == null) return;
            require(
                db.update(
                            "UPDATE orders SET pay_status=2,version=version+1 WHERE id=? AND"
                                + " status=6 AND pay_status=1",
                            task.get("order_id"))
                        == 1
                    || db.count(
                            "SELECT COUNT(*) FROM orders WHERE id=? AND status=6 AND pay_status=2",
                            task.get("order_id"))
                        == 1,
                409,
                "REFUND_STATE",
                "退款订单状态不正确");
            db.update(
                "UPDATE refund_task SET"
                    + " state='SUCCEEDED',lease_token=NULL,lease_until=NULL,last_error=NULL WHERE"
                    + " id=?",
                task.get("id"));
            events.append("ORDER_REFUNDED", Db.id(task, "order_id"));
            audit.write(null, "REFUND_SUCCESS", Db.id(task, "order_id"), "模拟退款完成");
          });
    } catch (Exception ex) {
      failure("refund_task", task, ex);
    }
    return true;
  }

  public boolean runEvent() {
    var task = claim("outbox_event");
    if (task == null) return false;
    try {
      if (task.get("event_type").equals("CACHE_INVALIDATE"))
        cache.retry(json.map(String.valueOf(task.get("payload"))).get("key").toString());
      else
        // Local sender acceptance is best effort, not a client delivery acknowledgement.
        socket.publish(
            json.write(
                Map.of(
                    "eventId",
                    task.get("id").toString(),
                    "event",
                    json.map(String.valueOf(task.get("payload"))))));
      db.update(
          "UPDATE outbox_event SET"
              + " state='SUCCEEDED',lease_until=NULL,lease_token=NULL,last_error=NULL WHERE id=?"
              + " AND state='PROCESSING' AND lease_token=?",
          task.get("id"),
          task.get("lease_token"));
    } catch (Exception ex) {
      failure("outbox_event", task, ex);
    }
    return true;
  }

  private Map<String, Object> claim(String table) {
    return tx.execute(
        s -> {
          LocalDateTime now = LocalDateTime.now(clock);
          var row =
              db.one(
                  "SELECT * FROM "
                      + table
                      + " FORCE INDEX("
                      + (table.equals("refund_task") ? "ix_refund_due" : "ix_outbox_due")
                      + ") WHERE (state='REQUESTED' AND next_attempt_at<=?) OR (state='PROCESSING'"
                      + " AND lease_until<?) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
                  now,
                  now);
          if (row == null) return null;
          String lease = UUID.randomUUID().toString();
          db.update(
              "UPDATE " + table + " SET state='PROCESSING',lease_until=?,lease_token=? WHERE id=?",
              now.plusSeconds(30),
              lease,
              row.get("id"));
          row.put("lease_token", lease);
          return row;
        });
  }

  private void failure(String table, Map<String, Object> task, Exception ex) {
    int attempts = Db.integer(task, "attempts") + 1;
    String error = ex instanceof com.sky.common.BusinessException b ? b.code() : "DELIVERY_FAILED";
    db.update(
        "UPDATE "
            + table
            + " SET state=?,attempts=?,next_attempt_at=?,lease_until=NULL,lease_token=NULL,last_error=?"
            + " WHERE id=? AND lease_token=?",
        attempts >= 5 ? "FAILED" : "REQUESTED",
        attempts,
        LocalDateTime.now(clock).plusSeconds(Math.min(300, 1L << attempts)),
        error,
        task.get("id"),
        task.get("lease_token"));
  }

  public List<Map<String, Object>> tasks(String type) {
    Actor.admin();
    return db.rows(
        "SELECT * FROM " + table(type) + " WHERE state<>'SUCCEEDED' ORDER BY id DESC LIMIT 100");
  }

  public void retry(String type, long id) {
    Actor.admin();
    require(
        db.update(
                "UPDATE "
                    + table(type)
                    + " SET state='REQUESTED',attempts=0,next_attempt_at=?,lease_until=NULL,lease_token=NULL,last_error=NULL"
                    + " WHERE id=? AND state='FAILED'",
                LocalDateTime.now(clock),
                id)
            == 1,
        409,
        "TASK_STATE",
        "仅失败任务可人工重试");
    audit.write(Actor.current(), "TASK_RETRY", id, type + " 手工重试");
  }

  private String table(String type) {
    require(type.equals("refund") || type.equals("outbox"), 400, "INVALID_TASK", "任务类型不正确");
    return type.equals("refund") ? "refund_task" : "outbox_event";
  }
}
