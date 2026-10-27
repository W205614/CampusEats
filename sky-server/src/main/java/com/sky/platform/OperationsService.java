package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.*;
import com.sky.security.Actor;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {
  public record Policy(
      @Min(1) @Max(1440) int acceptMinutes,
      @Min(1) @Max(1440) int prepareMinutes,
      @Min(1) @Max(1440) int dispatchMinutes,
      @Min(1) @Max(1440) int pickupMinutes,
      @Min(1) @Max(1440) int deliveryMinutes,
      @Min(1) @Max(1440) int selfPickupMinutes,
      @PositiveOrZero long version) {}

  public record ExceptionView(
      String id,
      String orderId,
      String orderNumber,
      String storeId,
      String campusId,
      String kind,
      String stage,
      String state,
      String reason,
      String version,
      LocalDateTime createdAt,
      LocalDateTime deadlineAt,
      boolean migrated) {}

  public record Acknowledge(
      @PositiveOrZero long version, @NotBlank @Size(max = 200) String reason) {}

  private final Db db;
  private final PlatformScope scope;
  private final CursorPages pages;
  private final Clock clock;
  private final FulfillmentStages stages;

  public OperationsService(
      Db db, PlatformScope scope, CursorPages pages, Clock clock, FulfillmentStages stages) {
    this.db = db;
    this.scope = scope;
    this.pages = pages;
    this.clock = clock;
    this.stages = stages;
  }

  public Policy policy(long store) {
    scope.merchant(store, true);
    var p = db.one("SELECT * FROM service_policy WHERE store_id=?", store);
    return p == null
        ? new Policy(10, 30, 15, 15, 60, 120, 0)
        : new Policy(
            Db.integer(p, "accept_minutes"),
            Db.integer(p, "prepare_minutes"),
            Db.integer(p, "dispatch_minutes"),
            Db.integer(p, "pickup_minutes"),
            Db.integer(p, "delivery_minutes"),
            Db.integer(p, "self_pickup_minutes"),
            Db.id(p, "version"));
  }

  @Transactional
  public void policy(long store, Policy p) {
    scope.merchant(store, true);
    db.update("INSERT IGNORE INTO service_policy(store_id) VALUES(?)", store);
    require(
        db.update(
                "UPDATE service_policy SET"
                    + " accept_minutes=?,prepare_minutes=?,dispatch_minutes=?,pickup_minutes=?,delivery_minutes=?,self_pickup_minutes=?,version=version+1"
                    + " WHERE store_id=? AND version=?",
                p.acceptMinutes(),
                p.prepareMinutes(),
                p.dispatchMinutes(),
                p.pickupMinutes(),
                p.deliveryMinutes(),
                p.selfPickupMinutes(),
                store,
                p.version())
            == 1,
        409,
        "VERSION_CONFLICT",
        "服务策略已变化");
    scope.audit("SERVICE_POLICY", store, store, "更新后续阶段时限");
  }

  public CursorPages.Page<ExceptionView> exceptions(
      Long store, String cursor, Integer limit, String state) {
    if (store == null) scope.platform();
    else scope.merchant(store, false);
    require(
        state == null || Set.of("OPEN", "ACKNOWLEDGED", "RESOLVED").contains(state),
        400,
        "INVALID_STATE",
        "异常状态不正确");
    int size = CursorPages.size(limit);
    String range = pages.scope("exceptions", store, state);
    var args = new ArrayList<Object>();
    String filter = "";
    if (store != null) {
      filter += " AND e.store_id=?";
      args.add(store);
    }
    if (state != null) {
      filter += " AND e.state=?";
      args.add(state);
    }
    args.add(pages.before(cursor, range));
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT e.*,o.number FROM order_exception e JOIN orders o ON o.id=e.order_id WHERE 1=1"
                + filter
                + " AND e.id<? ORDER BY e.id DESC LIMIT ?",
            args.toArray());
    var views =
        rows.stream()
            .map(
                r ->
                    new ExceptionView(
                        r.get("id").toString(),
                        r.get("order_id").toString(),
                        r.get("number").toString(),
                        r.get("store_id").toString(),
                        r.get("campus_id").toString(),
                        r.get("kind").toString(),
                        r.get("stage").toString(),
                        r.get("state").toString(),
                        (String) r.get("reason"),
                        r.get("version").toString(),
                        Db.time(r.get("created_at")),
                        r.get("deadline_at") == null ? null : Db.time(r.get("deadline_at")),
                        Db.bool(r, "migrated")))
            .toList();
    return pages.finish(views, size, range, r -> Long.parseLong(r.id()));
  }

  @Transactional
  public void acknowledge(long id, Acknowledge input) {
    var e = db.one("SELECT * FROM order_exception WHERE id=? FOR UPDATE", id);
    require(e != null, 404, "NOT_FOUND", "异常不存在");
    if (!Actor.employee().role().equals("PLATFORM_ADMIN"))
      scope.merchant(Db.id(e, "store_id"), false);
    require(!"RESOLVED".equals(e.get("state")), 409, "EXCEPTION_RESOLVED", "异常已解除");
    require(
        db.update(
                "UPDATE order_exception SET"
                    + " state='ACKNOWLEDGED',reason=?,acknowledged_at=?,version=version+1 WHERE"
                    + " id=? AND version=?",
                input.reason(),
                LocalDateTime.now(clock),
                id,
                input.version())
            == 1,
        409,
        "VERSION_CONFLICT",
        "异常已变化");
    scope.audit("EXCEPTION_ACK", id, Db.id(e, "store_id"), input.reason());
  }

  @Transactional
  public void detect(long order) {
    var o = db.one("SELECT * FROM orders WHERE id=? FOR UPDATE", order);
    if (o == null) return;
    int status = Db.integer(o, "status");
    stages.sync(order);
    var stage = db.one("SELECT * FROM order_stage WHERE order_id=?", order);
    var at = LocalDateTime.now(clock);
    // This compatibility field is derived from the frozen stage, not a second
    // timer. Correct old premature flags without changing the order's business
    // version or emitting the former fixed-60-minute event as well.
    if (o.get("checkout_id") != null && status >= 2 && status <= 4) {
      boolean overdue =
          status == 4
              && "DELIVERING".equals(stage.get("stage"))
              && stage.get("deadline_at") != null
              && !Db.time(stage.get("deadline_at")).isAfter(at);
      if (Db.bool(o, "delivery_overdue") != overdue)
        db.update("UPDATE orders SET delivery_overdue=? WHERE id=?", overdue, order);
    }
    if (stage.get("deadline_at") != null
        && !Db.time(stage.get("deadline_at")).isAfter(at))
      create(o, stage, "STAGE_OVERDUE");
    if (status >= 3
        && status <= 4
        && o.get("courier_id") != null
        && db.count(
                "SELECT COUNT(*) FROM employee WHERE id=? AND role='DELIVERER' AND status=1 AND"
                    + " campus_id=?",
                o.get("courier_id"),
                o.get("campus_id"))
            == 0) create(o, stage, "COURIER_UNAVAILABLE");
  }

  private void create(Map<String, Object> o, Map<String, Object> stage, String kind) {
    String cause = "";
    if (kind.equals("COURIER_UNAVAILABLE")) {
      var person = db.one("SELECT auth_version FROM employee WHERE id=?", o.get("courier_id"));
      cause = o.get("courier_id") + ":" + (person == null ? "missing" : person.get("auth_version"));
    }
    if (db.update(
            "INSERT IGNORE INTO"
                + " order_exception(order_id,store_id,campus_id,kind,stage_seq,stage,deadline_at,migrated,cause_key,created_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?)",
            o.get("id"),
            o.get("store_id"),
            o.get("campus_id"),
            kind,
            stage.get("stage_seq"),
            stage.get("stage"),
            stage.get("deadline_at"),
            stage.get("migrated"),
            cause,
            LocalDateTime.now(clock))
        == 1) scope.event(Db.id(o, "id"), "ORDER_EXCEPTION");
  }

  @Transactional
  public void availability(long staff, boolean enabled) {
    var a = Actor.employee();
    require(
        a.role().equals("PLATFORM_ADMIN") || a.role().equals("DELIVERER") && a.id() == staff,
        403,
        "FORBIDDEN",
        "无权修改接单状态");
    var p = db.one("SELECT * FROM employee WHERE id=? FOR UPDATE", staff);
    require(p != null && "DELIVERER".equals(p.get("role")), 404, "NOT_FOUND", "配送员不存在");
    db.update("UPDATE employee SET accepting_tasks=? WHERE id=?", enabled, staff);
    scope.audit("COURIER_AVAILABILITY", staff, null, enabled ? "接收新任务" : "停止接收新任务");
  }

  public Map<String, Boolean> availability() {
    var a = Actor.employee();
    require(a.role().equals("DELIVERER"), 403, "FORBIDDEN", "仅配送员可查询");
    var p = db.one("SELECT accepting_tasks FROM employee WHERE id=?", a.id());
    require(p != null, 404, "NOT_FOUND", "配送员不存在");
    return Map.of("enabled", Db.bool(p, "accepting_tasks"));
  }

  @Transactional
  public void emergency(long staff, String reason) {
    scope.platform();
    require(
        reason != null && !reason.isBlank() && reason.length() <= 200,
        400,
        "INVALID_REASON",
        "请填写停用原因");
    var p = db.one("SELECT * FROM employee WHERE id=? FOR UPDATE", staff);
    require(p != null && "DELIVERER".equals(p.get("role")), 404, "NOT_FOUND", "配送员不存在");
    db.update(
        "UPDATE employee SET status=0,accepting_tasks=false,auth_version=auth_version+1 WHERE id=?",
        staff);
    db.update(
        "UPDATE auth_session SET revoked=true WHERE subject_type='ADMIN' AND subject_id=?", staff);
    // No order lock is acquired under this person lock: scans see the committed disable and isolate
    // each order.
    scope.audit("COURIER_EMERGENCY", staff, null, reason);
  }

  public CursorPages.Page<Map<String, Object>> active(long staff, String cursor, Integer limit) {
    scope.platform();
    int size = CursorPages.size(limit);
    String range = pages.scope("active-delivery", staff);
    return pages.finish(
        db.rows(
            "SELECT id,number,status,version,store_id,campus_id FROM orders WHERE courier_id=? AND"
                + " status IN(3,4) AND id<? ORDER BY id DESC LIMIT ?",
            staff,
            pages.before(cursor, range),
            size + 1),
        size,
        range);
  }
}
