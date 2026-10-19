package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.business.QuotaService;
import com.sky.common.Hashes;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentService {
  private final Db db;
  private final PlatformScope scope;
  private final QuotaService quotas;
  private final Clock clock;
  private final com.sky.security.RateLimits limits;
  private final SecureRandom random = new SecureRandom();

  public FulfillmentService(
      Db db,
      PlatformScope scope,
      QuotaService quotas,
      Clock clock,
      com.sky.security.RateLimits limits) {
    this.db = db;
    this.scope = scope;
    this.quotas = quotas;
    this.clock = clock;
    this.limits = limits;
  }

  public List<Map<String, Object>> orders(Long store) {
    return orders(store, null);
  }

  public List<Map<String, Object>> orders(Long store, Long beforeId) {
    require(beforeId == null || beforeId > 0, 400, "INVALID_CURSOR", "分页游标须为正整数");
    Actor a = Actor.current();
    String where;
    var args = new ArrayList<Object>();
    if (a.type().equals("USER")) {
      where = "o.user_id=?";
      args.add(a.id());
    } else if (a.role().equals("DELIVERER")) {
      require(a.campusId() != null, 403, "FORBIDDEN", "无配送校区");
      where = "o.courier_id=? AND o.campus_id=?";
      args.add(a.id());
      args.add(a.campusId());
    } else {
      require(store != null, 400, "STORE_REQUIRED", "请选择店铺");
      scope.merchant(store, false);
      where = "o.store_id=?";
      args.add(store);
    }
    if (beforeId != null) {
      where += " AND o.id<?";
      args.add(beforeId);
    }
    var rows =
        db.rows(
            "SELECT o.*,s.name store_name,c.name campus_name FROM orders o JOIN store s ON"
                + " s.id=o.store_id JOIN campus c ON c.id=o.campus_id WHERE "
                + where
                + " ORDER BY o.id DESC LIMIT 100",
            args.toArray());
    decorateAll(rows, a);
    return rows;
  }

  public Map<String, Object> detail(long id) {
    var row = scope.order(id, false);
    row.put("store_name", scope.store(Db.id(row, "store_id"), false).get("name"));
    decorateAll(List.of(row), Actor.current());
    return row;
  }

  private void decorateAll(List<Map<String, Object>> rows, Actor a) {
    if (rows.isEmpty()) return;
    var ids = rows.stream().map(r -> Db.id(r, "id")).toList();
    var details = grouped("order_detail", ids);
    var deliveries = grouped("delivery_task", ids);
    var aftersales = grouped("aftersale", ids);
    var refunds = grouped("refund_request", ids);
    var reviews = grouped("review", ids);
    for (var row : rows) {
      long id = Db.id(row, "id");
      row.remove("pickup_hash");
      row.remove("request_hash");
      if (!a.type().equals("USER")) row.remove("pickup_code");
      row.put("details", details.getOrDefault(id, List.of()));
      var own = deliveries.getOrDefault(id, List.of());
      row.put("delivery", own.isEmpty() ? null : own.getFirst());
      row.put("aftersales", aftersales.getOrDefault(id, List.of()));
      row.put("refunds", refunds.getOrDefault(id, List.of()));
      row.put("reviewed", reviews.containsKey(id));
    }
  }

  private Map<Long, List<Map<String, Object>>> grouped(String table, List<Long> ids) {
    var rows =
        db.named.queryForList(
            "SELECT * FROM " + table + " WHERE order_id IN (:ids) ORDER BY id", Map.of("ids", ids));
    var result = new HashMap<Long, List<Map<String, Object>>>();
    for (var row : rows) {
      row.remove("lease_token");
      result.computeIfAbsent(Db.id(row, "order_id"), ignored -> new ArrayList<>()).add(row);
    }
    return result;
  }

  @Transactional
  public void reminder(long id) {
    Actor a = Actor.user();
    var order = scope.order(id, false);
    int status = Db.integer(order, "status");
    require(status >= 2 && status <= 4, 409, "ORDER_STATE", "当前订单不支持催单");
    limits.check("reminder:" + a.id() + ":" + id, 1, 60);
    scope.event(id, "ORDER_REMINDER");
    scope.audit("ORDER_REMINDER", id, Db.id(order, "store_id"), "用户催单");
  }

  @Transactional
  public Map<String, Object> action(long id, String action, PlatformCommands.Action input) {
    var o = scope.order(id, true);
    Actor a = Actor.current();
    int status = Db.integer(o, "status");
    long store = Db.id(o, "store_id");
    require(Db.id(o, "version") == input.version(), 409, "VERSION_CONFLICT", "订单状态已变化");
    if (Set.of("ready", "publish", "assign", "pickup", "complete").contains(action))
      requireNoCancellation(id);
    switch (action) {
      case "accept" -> {
        scope.merchant(store, false);
        require(status == 2, 409, "ORDER_STATE", "仅待接单订单可接单");
        quotas.transition(id, true);
        db.update("UPDATE orders SET status=3,version=version+1 WHERE id=?", id);
      }
      case "reject", "cancel" -> {
        if (a.type().equals("USER")) require(status == 2, 409, "ORDER_STATE", "用户仅能直接取消待接单订单");
        else {
          scope.merchant(store, false);
          require(
              status == 2 || status == 3 && !Db.bool(o, "ready"), 409, "ORDER_STATE", "当前不能直接取消");
        }
        require(
            input.reason() != null && !input.reason().isBlank(), 400, "REASON_REQUIRED", "请填写取消原因");
        cancelPaid(o, input.reason());
      }
      case "ready" -> {
        scope.merchant(store, false);
        require(status == 3 && !Db.bool(o, "ready"), 409, "ORDER_STATE", "订单尚未接单或已备餐完成");
        String code =
            o.get("fulfillment").equals("PICKUP")
                ? String.format("%08d", random.nextInt(100000000))
                : null;
        db.update(
            "UPDATE orders SET ready=true,pickup_code=?,pickup_hash=?,version=version+1 WHERE id=?",
            code,
            code == null ? null : Hashes.sha256(id + ":" + code),
            id);
      }
      case "publish", "assign" -> {
        scope.merchant(store, false);
        require(
            status == 3 && Db.bool(o, "ready") && o.get("fulfillment").equals("DELIVERY"),
            409,
            "ORDER_STATE",
            "仅备餐完成的配送订单可派送");
        var task = db.one("SELECT * FROM delivery_task WHERE order_id=? FOR UPDATE", id);
        require(
            task == null || task.get("state").equals("OPEN"), 409, "DELIVERY_CLAIMED", "任务已被领取");
        if (task == null)
          db.update(
              "INSERT INTO delivery_task(order_id,campus_id) VALUES(?,?)", id, o.get("campus_id"));
        if (action.equals("assign")) {
          require(
              input.courierId() != null
                  && db.count(
                          "SELECT COUNT(*) FROM employee WHERE id=? AND role='DELIVERER' AND"
                              + " status=1 AND campus_id=?",
                          input.courierId(),
                          o.get("campus_id"))
                      == 1,
              400,
              "INVALID_COURIER",
              "请选择本校区启用的配送员");
          require(
              db.update(
                      "UPDATE delivery_task SET courier_id=?,state='ASSIGNED',version=version+1"
                          + " WHERE order_id=? AND state='OPEN'",
                      input.courierId(),
                      id)
                  == 1,
              409,
              "DELIVERY_CLAIMED",
              "任务已被领取");
          db.update("UPDATE orders SET courier_id=? WHERE id=?", input.courierId(), id);
        }
        db.update("UPDATE orders SET version=version+1 WHERE id=?", id);
      }
      case "pickup" -> {
        scope.courier(Db.id(o, "campus_id"));
        require(
            status == 3 && Objects.equals(o.get("courier_id"), a.id()),
            409,
            "ORDER_STATE",
            "无可取餐配送任务");
        require(
            db.update(
                    "UPDATE delivery_task SET state='PICKED',version=version+1 WHERE order_id=? AND"
                        + " courier_id=? AND state='ASSIGNED'",
                    id,
                    a.id())
                == 1,
            409,
            "DELIVERY_STATE",
            "配送任务已变化");
        db.update(
            "UPDATE orders SET status=4,delivery_started_at=?,version=version+1 WHERE id=?",
            now(),
            id);
      }
      case "complete" -> {
        scope.courier(Db.id(o, "campus_id"));
        require(status == 4, 409, "ORDER_STATE", "订单尚未取餐");
        db.update(
            "UPDATE delivery_task SET state='COMPLETED',version=version+1 WHERE order_id=? AND"
                + " courier_id=?",
            id,
            a.id());
        db.update(
            "UPDATE orders SET status=5,completed_at=?,delivery_time=?,version=version+1 WHERE"
                + " id=?",
            now(),
            now(),
            id);
      }
      case "unassign" -> {
        scope.platform();
        require(status == 3, 409, "ORDER_STATE", "取餐后不能撤回配送分配");
        require(
            db.update(
                    "UPDATE delivery_task SET courier_id=NULL,state='OPEN',version=version+1 WHERE"
                        + " order_id=? AND state='ASSIGNED'",
                    id)
                == 1,
            409,
            "DELIVERY_STATE",
            "任务未分配");
        db.update("UPDATE orders SET courier_id=NULL,version=version+1 WHERE id=?", id);
      }
      default -> require(false, 400, "INVALID_ACTION", "不支持的订单操作");
    }
    scope.event(id, "ORDER_" + action.toUpperCase(Locale.ROOT));
    scope.audit(
        "ORDER_" + action.toUpperCase(Locale.ROOT),
        id,
        store,
        Objects.toString(input.reason(), "状态更新"));
    return detail(id);
  }

  @Transactional
  public Map<String, Object> reassign(long id, PlatformCommands.Reassign input) {
    scope.platform();
    String reason = exceptionReason(input.reason());
    var order = scope.order(id, true);
    require(Db.id(order, "version") == input.version(), 409, "VERSION_CONFLICT", "订单状态已变化，请刷新");
    int status = Db.integer(order, "status");
    require(
        "DELIVERY".equals(order.get("fulfillment"))
            && (status == 3 && Db.bool(order, "ready") || status == 4)
            && order.get("courier_id") != null,
        409,
        "ORDER_STATE",
        "仅已分配或配送中的配送订单可改派");
    requireNoCancellation(id);
    var courier = db.one("SELECT * FROM employee WHERE id=? FOR SHARE", input.courierId());
    require(
        courier != null
            && "DELIVERER".equals(courier.get("role"))
            && Db.integer(courier, "status") == 1
            && Objects.equals(courier.get("campus_id"), order.get("campus_id"))
            && !Objects.equals(input.courierId(), order.get("courier_id")),
        400,
        "INVALID_COURIER",
        "请选择本校区启用的其他配送员");
    var task = db.one("SELECT * FROM delivery_task WHERE order_id=? FOR UPDATE", id);
    String expected = status == 4 ? "PICKED" : "ASSIGNED";
    require(
        task != null
            && expected.equals(task.get("state"))
            && Objects.equals(task.get("courier_id"), order.get("courier_id")),
        409,
        "DELIVERY_STATE",
        "配送任务已变化，请刷新");
    db.update(
        "UPDATE delivery_task SET courier_id=?,version=version+1 WHERE order_id=?",
        input.courierId(),
        id);
    db.update("UPDATE orders SET courier_id=?,version=version+1 WHERE id=?", input.courierId(), id);
    long store = Db.id(order, "store_id");
    scope.audit("ORDER_REASSIGN", id, store, reason);
    // Reasons may use all 200 audit characters; keep assignment metadata in a separate row.
    scope.audit(
        "DELIVERY_REASSIGN",
        id,
        store,
        "courier=" + order.get("courier_id") + "->" + input.courierId() + ";state=" + expected);
    scope.event(id, "ORDER_REASSIGNED");
    return detail(id);
  }

  @Transactional
  public Map<String, Object> exceptionCancel(long id, PlatformCommands.ExceptionCancel input) {
    scope.platform();
    String reason = exceptionReason(input.reason());
    var order = scope.order(id, true);
    require(Db.id(order, "version") == input.version(), 409, "VERSION_CONFLICT", "订单状态已变化，请刷新");
    int status = Db.integer(order, "status");
    require(
        "DELIVERY".equals(order.get("fulfillment"))
            && (status == 3 && Db.bool(order, "ready") || status == 4),
        409,
        "ORDER_STATE",
        "仅已备餐或配送中的配送订单可异常取消");
    require(
        Db.id(order, "reserved_refund_cents") == 0
            && db.count(
                    "SELECT COUNT(*) FROM aftersale WHERE order_id=? AND state"
                        + " IN('PENDING','ESCALATED','APPROVED')",
                    id)
                == 0,
        409,
        "AFTERSALE_PENDING",
        "请先处理当前售后申请或退款任务");
    db.update(
        "UPDATE orders SET status=6,cancel_time=?,cancel_reason=?,version=version+1 WHERE id=?",
        now(),
        reason,
        id);
    db.update("UPDATE delivery_task SET state='CANCELLED',version=version+1 WHERE order_id=?", id);
    reserveCancellationRefund(order);
    long store = Db.id(order, "store_id");
    scope.audit("ORDER_EXCEPTION_CANCEL", id, store, reason);
    scope.audit(
        "DELIVERY_EXCEPTION_CANCEL",
        id,
        store,
        "courier="
            + order.get("courier_id")
            + "->"
            + order.get("courier_id")
            + ";status="
            + status
            + "->6");
    scope.event(id, "ORDER_EXCEPTION_CANCELLED");
    return detail(id);
  }

  private String exceptionReason(String value) {
    require(value != null && !value.isBlank(), 400, "REASON_REQUIRED", "请填写操作原因");
    require(value.length() <= 200, 400, "INVALID_REASON", "操作原因不能超过200字");
    return value.trim();
  }

  /** Return failures normally so the failed-attempt counter is committed. */
  @Transactional
  public Map<String, Object> verify(long id, PlatformCommands.Action input) {
    var o = scope.order(id, true);
    scope.merchant(Db.id(o, "store_id"), false);
    requireNoCancellation(id);
    require(
        Db.id(o, "version") == input.version()
            && Db.integer(o, "status") == 3
            && Db.bool(o, "ready")
            && o.get("fulfillment").equals("PICKUP"),
        409,
        "ORDER_STATE",
        "当前订单不能核销");
    require(Db.integer(o, "pickup_attempts") < 5, 429, "PICKUP_LOCKED", "核销错误次数过多，请平台重置");
    boolean valid =
        input.code() != null && Hashes.sha256(id + ":" + input.code()).equals(o.get("pickup_hash"));
    if (!valid) {
      db.update("UPDATE orders SET pickup_attempts=pickup_attempts+1 WHERE id=?", id);
      return Map.of("verified", false, "remaining", 4 - Db.integer(o, "pickup_attempts"));
    }
    db.update(
        "UPDATE orders SET"
            + " status=5,completed_at=?,pickup_hash=NULL,pickup_code=NULL,version=version+1 WHERE"
            + " id=?",
        now(),
        id);
    scope.event(id, "ORDER_COMPLETED");
    scope.audit("PICKUP_VERIFY", id, Db.id(o, "store_id"), "自取核销完成");
    return Map.of("verified", true);
  }

  private void requireNoCancellation(long order) {
    require(
        db.count(
                "SELECT COUNT(*) FROM aftersale WHERE order_id=? AND kind='CANCEL' AND state"
                    + " IN('PENDING','ESCALATED')",
                order)
            == 0,
        409,
        "CANCELLATION_PENDING",
        "请先处理用户的取消申请");
  }

  @Transactional
  public void resetCode(long id) {
    scope.platform();
    var o = scope.order(id, true);
    require(
        Db.integer(o, "status") == 3
            && Db.bool(o, "ready")
            && o.get("fulfillment").equals("PICKUP"),
        409,
        "ORDER_STATE",
        "当前不能重置核销码");
    String code = String.format("%08d", random.nextInt(100000000));
    db.update(
        "UPDATE orders SET pickup_attempts=0,pickup_code=?,pickup_hash=?,version=version+1 WHERE"
            + " id=?",
        code,
        Hashes.sha256(id + ":" + code),
        id);
    scope.audit("PICKUP_RESET", id, Db.id(o, "store_id"), "平台重置核销码");
  }

  public List<Map<String, Object>> pool() {
    Actor a = Actor.employee();
    require(a.campusId() != null, 403, "FORBIDDEN", "无配送校区");
    scope.courier(a.campusId());
    return db.rows(
        "SELECT t.id,t.order_id,t.version,s.name store_name,b.name building_name,o.ready FROM"
            + " delivery_task t JOIN orders o ON o.id=t.order_id JOIN store s ON s.id=o.store_id"
            + " LEFT JOIN campus_building b ON b.name=o.building_name AND b.campus_id=o.campus_id"
            + " WHERE t.campus_id=? AND t.state='OPEN' AND o.status=3 AND NOT EXISTS (SELECT 1"
            + " FROM aftersale a WHERE a.order_id=o.id AND a.kind='CANCEL' AND a.state"
            + " IN('PENDING','ESCALATED')) ORDER BY t.id LIMIT 100",
        a.campusId());
  }

  @Transactional
  public void claim(long task, long version) {
    var initial = db.one("SELECT order_id,campus_id FROM delivery_task WHERE id=?", task);
    require(initial != null, 404, "NOT_FOUND", "任务不存在");
    Actor a = scope.courier(Db.id(initial, "campus_id"));
    var o = db.one("SELECT * FROM orders WHERE id=? FOR UPDATE", initial.get("order_id"));
    require(Db.integer(o, "status") == 3, 409, "ORDER_STATE", "订单已不能配送");
    requireNoCancellation(Db.id(o, "id"));
    require(
        db.update(
                "UPDATE delivery_task SET courier_id=?,state='ASSIGNED',version=version+1 WHERE"
                    + " id=? AND state='OPEN' AND version=?",
                a.id(),
                task,
                version)
            == 1,
        409,
        "DELIVERY_CLAIMED",
        "任务已被抢走或状态变化");
    db.update("UPDATE orders SET courier_id=?,version=version+1 WHERE id=?", a.id(), o.get("id"));
    scope.event(Db.id(o, "id"), "DELIVERY_ASSIGNED");
    scope.audit("DELIVERY_CLAIM", Db.id(o, "id"), Db.id(o, "store_id"), "配送员抢单");
  }

  public void cancelPaid(Map<String, Object> o, String reason) {
    long id = Db.id(o, "id");
    require(
        db.count(
                "SELECT COUNT(*) FROM aftersale WHERE order_id=? AND state"
                    + " IN('PENDING','ESCALATED','APPROVED')",
                id)
            == 0,
        409,
        "AFTERSALE_PENDING",
        "请先处理当前售后申请");
    int status = Db.integer(o, "status");
    require(status == 2 || status == 3, 409, "ORDER_STATE", "当前订单不可取消");
    if (status == 2) quotas.transition(id, false);
    db.update(
        "UPDATE orders SET status=6,cancel_time=?,cancel_reason=?,version=version+1 WHERE id=?",
        now(),
        reason,
        id);
    db.update("UPDATE delivery_task SET state='CANCELLED',version=version+1 WHERE order_id=?", id);
    reserveCancellationRefund(o);
  }

  private void reserveCancellationRefund(Map<String, Object> o) {
    long id = Db.id(o, "id");
    long amount =
        Db.id(o, "paid_cents") - Db.id(o, "refunded_cents") - Db.id(o, "reserved_refund_cents");
    if (amount > 0) {
      db.update(
          "UPDATE orders SET reserved_refund_cents=reserved_refund_cents+? WHERE id=?", amount, id);
      db.insert(
          "INSERT INTO refund_request(order_id,store_id,amount_cents,next_attempt_at,created_at)"
              + " VALUES(?,?,?,?,?)",
          id,
          o.get("store_id"),
          amount,
          now(),
          now());
    }
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
