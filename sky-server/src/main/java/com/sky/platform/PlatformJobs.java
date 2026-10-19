package com.sky.platform;

import com.sky.business.PaymentGateway;
import com.sky.infra.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PlatformJobs {
  private final Db db;
  private final Clock clock;
  private final PaymentGateway gateway;
  private final CheckoutService checkouts;
  private final PlatformScope scope;
  private final TransactionTemplate tx;
  private final boolean enabled;
  private final RuntimeOperations operations;
  private final com.sky.business.OrderCommandService orders;
  private final ExpirySweep checkoutExpiry = new ExpirySweep(), deliveryExpiry = new ExpirySweep();

  public PlatformJobs(
      Db db,
      Clock clock,
      PaymentGateway gateway,
      CheckoutService checkouts,
      PlatformScope scope,
      PlatformTransactionManager manager,
      @Value("${campus.tasks-enabled:true}") boolean enabled,
      RuntimeOperations operations,
      com.sky.business.OrderCommandService orders) {
    this.db = db;
    this.clock = clock;
    this.gateway = gateway;
    this.checkouts = checkouts;
    this.scope = scope;
    this.tx = new TransactionTemplate(manager);
    this.enabled = enabled;
    this.operations = operations;
    this.orders = orders;
  }

  @Scheduled(fixedDelay = 2000)
  public void work() {
    if (!enabled) return;
    operations.run(
        "platformRefunds",
        round -> {
          for (int i = 0; i < 20 && round.withinBudget() && refund(round::failed); i++) {}
        });
  }

  @Scheduled(fixedDelay = 2000)
  public void expireCheckouts() {
    if (!enabled) return;
    operations.run(
        "checkoutExpiry",
        round ->
            checkoutExpiry.run(
                round,
                cursor ->
                    db.rows(
                        "SELECT id FROM checkout WHERE state='PENDING' AND expires_at<=?"
                            + " AND id>? ORDER BY id LIMIT 100",
                        now(),
                        cursor),
                checkouts::expire));
  }

  @Scheduled(fixedDelay = 30000)
  public void markOverdue() {
    if (!enabled) return;
    operations.run(
        "deliveryOverdue",
        round ->
            deliveryExpiry.run(
                round,
                cursor ->
                    db.rows(
                        "SELECT id FROM orders WHERE status=4 AND delivery_overdue=false"
                            + " AND delivery_started_at<? AND id>? ORDER BY id LIMIT 100",
                        now().minusMinutes(60),
                        cursor),
                orders::markOverdue));
  }

  public boolean refund() {
    return refund((ex, id) -> {});
  }

  private boolean refund(java.util.function.BiConsumer<Exception, Object> onFailure) {
    var task =
        tx.execute(
            s -> {
              var r =
                  db.one(
                      "SELECT * FROM refund_request FORCE INDEX(ix_refund_request_due) WHERE"
                          + " (state='REQUESTED' AND next_attempt_at<=?) OR (state='PROCESSING' AND"
                          + " lease_until<?) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
                      now(),
                      now());
              if (r == null) return null;
              String token = UUID.randomUUID().toString();
              db.update(
                  "UPDATE refund_request SET state='PROCESSING',lease_token=?,lease_until=? WHERE"
                      + " id=?",
                  token,
                  now().plusSeconds(30),
                  r.get("id"));
              r.put("lease_token", token);
              return r;
            });
    if (task == null) return false;
    try {
      var payment = db.one("SELECT checkout_id FROM orders WHERE id=?", task.get("order_id"));
      gateway.refund(
          new PaymentGateway.Refund(
              "refund-request:" + task.get("id"),
              "checkout:" + payment.get("checkout_id"),
              Db.id(task, "amount_cents"),
              "CNY"));
      tx.executeWithoutResult(
          s -> {
            var order = db.one("SELECT * FROM orders WHERE id=? FOR UPDATE", task.get("order_id"));
            var current =
                db.one(
                    "SELECT * FROM refund_request WHERE id=? AND state='PROCESSING' AND"
                        + " lease_token=? FOR UPDATE",
                    task.get("id"),
                    task.get("lease_token"));
            if (current == null) return;
            long amount = Db.id(current, "amount_cents");
            com.sky.common.BusinessException.require(
                Db.id(order, "reserved_refund_cents") >= amount
                    && Db.id(order, "refunded_cents") + amount <= Db.id(order, "paid_cents"),
                409,
                "REFUND_AMOUNT",
                "退款账目不一致");
            if (current.get("aftersale_id") != null) {
              for (var item :
                  db.rows(
                      "SELECT * FROM aftersale_item WHERE request_id=? ORDER BY detail_id",
                      current.get("aftersale_id")))
                com.sky.common.BusinessException.require(
                    db.update(
                            "UPDATE order_detail SET"
                                + " reserved_quantity=reserved_quantity-?,refunded_quantity=refunded_quantity+?"
                                + " WHERE id=? AND reserved_quantity>=?",
                            item.get("quantity"),
                            item.get("quantity"),
                            item.get("detail_id"),
                            item.get("quantity"))
                        == 1,
                    409,
                    "REFUND_QUANTITY",
                    "退款数量账目不一致");
              db.update(
                  "UPDATE aftersale SET state='REFUNDED',version=version+1 WHERE id=?",
                  current.get("aftersale_id"));
            }
            db.update(
                "UPDATE orders SET"
                    + " refunded_cents=refunded_cents+?,reserved_refund_cents=reserved_refund_cents-?,pay_status=CASE"
                    + " WHEN refunded_cents=paid_cents THEN 2 ELSE 3 END,version=version+1 WHERE"
                    + " id=?",
                amount,
                amount,
                order.get("id"));
            db.update(
                "UPDATE refund_request SET"
                    + " state='SUCCEEDED',lease_token=NULL,lease_until=NULL,last_error=NULL,succeeded_at=?"
                    + " WHERE id=?",
                now(),
                current.get("id"));
            scope.event(Db.id(order, "id"), "ORDER_REFUNDED");
            db.update(
                "INSERT INTO"
                    + " audit_log(actor_type,actor_id,action,target_id,detail,created_at,store_id,campus_id)"
                    + " VALUES('SYSTEM',0,'REFUND_SUCCESS',?,'模拟退款完成',?,?,?)",
                current.get("id"),
                now(),
                order.get("store_id"),
                order.get("campus_id"));
          });
    } catch (Exception ex) {
      onFailure.accept(ex, task.get("id"));
      int attempts = Db.integer(task, "attempts") + 1;
      db.update(
          "UPDATE refund_request SET"
              + " state=?,attempts=?,next_attempt_at=?,lease_until=NULL,lease_token=NULL,last_error=?"
              + " WHERE id=? AND lease_token=? AND state='PROCESSING'",
          attempts >= 5 ? "FAILED" : "REQUESTED",
          attempts,
          now().plusSeconds(Math.min(300, 1L << attempts)),
          ex instanceof com.sky.common.BusinessException b ? b.code() : "REFUND_FAILED",
          task.get("id"),
          task.get("lease_token"));
    }
    return true;
  }

  public List<Map<String, Object>> refunds(Long store) {
    if (store == null) scope.platform();
    else scope.merchant(store, false);
    return db.rows(
        "SELECT * FROM refund_request"
            + (store == null ? "" : " WHERE store_id=?")
            + " ORDER BY id DESC LIMIT 100",
        store == null ? new Object[] {} : new Object[] {store});
  }

  public void retry(long id) {
    var r = db.one("SELECT * FROM refund_request WHERE id=?", id);
    com.sky.common.BusinessException.require(r != null, 404, "NOT_FOUND", "退款任务不存在");
    scope.merchant(Db.id(r, "store_id"), true);
    com.sky.common.BusinessException.require(
        db.update(
                "UPDATE refund_request SET"
                    + " state='REQUESTED',attempts=0,next_attempt_at=?,lease_token=NULL,lease_until=NULL"
                    + " WHERE id=? AND state='FAILED'",
                now(),
                id)
            == 1,
        409,
        "TASK_STATE",
        "仅失败任务可重试");
    scope.audit("REFUND_RETRY", id, Db.id(r, "store_id"), "人工恢复退款任务");
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public List<Map<String, Object>> events(Long store) {
    if (store == null) scope.platform();
    else scope.merchant(store, true);
    return db.rows(
        "SELECT id,event_type,aggregate_id,state,attempts,last_error,next_attempt_at,store_id FROM"
            + " outbox_event"
            + (store == null ? "" : " WHERE store_id=?")
            + " ORDER BY id DESC LIMIT 100",
        store == null ? new Object[] {} : new Object[] {store});
  }

  public void retryEvent(long id) {
    var event = db.one("SELECT store_id FROM outbox_event WHERE id=?", id);
    com.sky.common.BusinessException.require(event != null, 404, "NOT_FOUND", "通知任务不存在");
    Long store = event.get("store_id") == null ? null : Db.id(event, "store_id");
    if (store == null) scope.platform();
    else scope.merchant(store, true);
    com.sky.common.BusinessException.require(
        db.update(
                "UPDATE outbox_event SET"
                    + " state='REQUESTED',attempts=0,next_attempt_at=?,lease_token=NULL,lease_until=NULL"
                    + " WHERE id=? AND state='FAILED'",
                now(),
                id)
            == 1,
        409,
        "TASK_STATE",
        "仅失败任务可重试");
    scope.audit("OUTBOX_RETRY", id, store, "人工恢复通知任务");
  }
}
