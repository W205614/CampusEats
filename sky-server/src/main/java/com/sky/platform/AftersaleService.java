package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.*;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AftersaleService {
  private final Db db;
  private final PlatformScope scope;
  private final FulfillmentService fulfillment;
  private final Clock clock;

  public AftersaleService(Db db, PlatformScope scope, FulfillmentService fulfillment, Clock clock) {
    this.db = db;
    this.scope = scope;
    this.fulfillment = fulfillment;
    this.clock = clock;
  }

  @Transactional
  public long apply(long order, PlatformCommands.Aftersale input) {
    long user = Actor.user().id();
    var o = scope.order(order, true);
    int status = Db.integer(o, "status");
    boolean cancel = input.kind().equals("CANCEL");
    require(
        cancel
            ? status == 3
            : status == 5
                && o.get("completed_at") != null
                && !now().isAfter(Db.time(o.get("completed_at")).plusDays(7)),
        409,
        "AFTERSALE_WINDOW",
        "备餐中可申请取消；已完成订单7天内可申请退款");
    require(
        db.count(
                "SELECT COUNT(*) FROM aftersale WHERE order_id=? AND state"
                    + " IN('PENDING','ESCALATED','APPROVED')",
                order)
            == 0,
        409,
        "AFTERSALE_PENDING",
        "请先处理该订单已有申请");
    long id =
        db.insert(
            "INSERT INTO"
                + " aftersale(order_id,user_id,store_id,kind,state,reason,amount_cents,created_at)"
                + " VALUES(?,?,?,?,'PENDING',?,0,?)",
            order,
            user,
            o.get("store_id"),
            input.kind(),
            input.reason(),
            now());
    reserve(id, o, input.kind(), input.items());
    if (input.evidenceIds() != null) {
      require(
          new HashSet<>(input.evidenceIds()).size() == input.evidenceIds().size(),
          400,
          "INVALID_EVIDENCE",
          "举证图片重复");
      for (String evidence : input.evidenceIds())
        require(
            db.update(
                    "UPDATE evidence SET request_id=? WHERE id=? AND user_id=? AND order_id=? AND"
                        + " request_id IS NULL",
                    id,
                    evidence,
                    user,
                    order)
                == 1,
            400,
            "INVALID_EVIDENCE",
            "图片不属于当前订单或已绑定其他申请");
    }
    scope.audit("AFTERSALE_APPLY", id, Db.id(o, "store_id"), input.reason());
    scope.event(order, "AFTERSALE_APPLY");
    return id;
  }

  private void reserve(
      long id, Map<String, Object> o, String kind, List<PlatformCommands.RefundLine> requested) {
    long order = Db.id(o, "id");
    var details =
        db.rows("SELECT * FROM order_detail WHERE order_id=? ORDER BY id FOR UPDATE", order);
    var selected = new TreeMap<Long, Integer>();
    if (!kind.equals("PARTIAL")) {
      for (var d : details) {
        int qty =
            Db.integer(d, "number")
                - Db.integer(d, "refunded_quantity")
                - Db.integer(d, "reserved_quantity");
        if (qty > 0) selected.put(Db.id(d, "id"), qty);
      }
    } else {
      require(
          requested != null && !requested.isEmpty(), 400, "REFUND_ITEMS_REQUIRED", "请选择退款商品及数量");
      for (var l : requested)
        require(selected.put(l.detailId(), l.quantity()) == null, 400, "DUPLICATE_ITEM", "退款商品重复");
    }
    require(
        !selected.isEmpty()
            || !kind.equals("PARTIAL") && Db.id(o, "paid_cents") > Db.id(o, "refunded_cents"),
        409,
        "NOT_REFUNDABLE",
        "没有可退款商品或费用");
    long amount = 0;
    for (var e : selected.entrySet()) {
      var d = details.stream().filter(x -> Db.id(x, "id") == e.getKey()).findFirst().orElse(null);
      require(d != null, 400, "INVALID_REFUND_ITEM", "请选择本订单商品");
      int offset = Db.integer(d, "refunded_quantity") + Db.integer(d, "reserved_quantity");
      require(
          e.getValue() <= Db.integer(d, "number") - offset, 409, "REFUND_QUANTITY", "申请数量超出剩余可退数量");
      long line =
          Money.quantityAmount(
              Db.id(d, "line_total_cents"), Db.integer(d, "number"), offset, e.getValue());
      amount += line;
      db.update(
          "INSERT INTO aftersale_item(request_id,detail_id,quantity,amount_cents) VALUES(?,?,?,?)",
          id,
          e.getKey(),
          e.getValue(),
          line);
      db.update(
          "UPDATE order_detail SET reserved_quantity=reserved_quantity+? WHERE id=?",
          e.getValue(),
          e.getKey());
    }
    long available =
        Db.id(o, "paid_cents") - Db.id(o, "refunded_cents") - Db.id(o, "reserved_refund_cents");
    long fees = kind.equals("PARTIAL") ? 0 : available - amount;
    require(fees >= 0 && amount + fees <= available, 409, "REFUND_AMOUNT", "退款金额超出剩余实付");
    amount += fees;
    db.update("UPDATE aftersale SET amount_cents=?,fee_cents=? WHERE id=?", amount, fees, id);
    db.update(
        "UPDATE orders SET reserved_refund_cents=reserved_refund_cents+? WHERE id=?",
        amount,
        order);
  }

  private Map<String, Object> request(long id, boolean lock) {
    var r = db.one("SELECT * FROM aftersale WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    require(r != null, 404, "NOT_FOUND", "售后申请不存在");
    scope.order(Db.id(r, "order_id"), false);
    return r;
  }

  public List<Map<String, Object>> list(Long store, boolean platform) {
    if (platform) scope.platform();
    else if (store != null) scope.merchant(store, false);
    else Actor.user();
    var rows =
        db.rows(
            "SELECT a.*,o.number FROM aftersale a JOIN orders o ON o.id=a.order_id"
                + (platform ? "" : store != null ? " WHERE a.store_id=?" : " WHERE a.user_id=?")
                + " ORDER BY a.id DESC LIMIT 100",
            platform ? new Object[] {} : new Object[] {store == null ? Actor.user().id() : store});
    for (var r : rows) {
      r.put(
          "items",
          db.rows(
              "SELECT i.*,d.name FROM aftersale_item i JOIN order_detail d ON d.id=i.detail_id"
                  + " WHERE request_id=?",
              r.get("id")));
      r.put("evidence", db.rows("SELECT id FROM evidence WHERE request_id=?", r.get("id")));
    }
    return rows;
  }

  @Transactional
  public void decide(long id, PlatformCommands.Decision input, boolean platform) {
    var initial = request(id, false);
    var o = scope.order(Db.id(initial, "order_id"), true);
    var r = request(id, true);
    if (platform) {
      scope.platform();
      require(r.get("state").equals("ESCALATED"), 409, "AFTERSALE_STATE", "仅介入中的申请由平台裁决");
    } else {
      scope.merchant(Db.id(o, "store_id"), false);
      require(r.get("state").equals("PENDING"), 409, "AFTERSALE_STATE", "申请已处理");
    }
    require(Db.id(r, "version") == input.version(), 409, "VERSION_CONFLICT", "申请已变化");
    if (!input.approve()) {
      release(r);
      db.update(
          "UPDATE aftersale SET state='REJECTED',decision=?,version=version+1 WHERE id=?",
          input.reason(),
          id);
    } else {
      if (r.get("kind").equals("CANCEL")) {
        require(Db.integer(o, "status") == 3, 409, "ORDER_STATE", "订单已开始配送或完成，不能取消");
        db.update(
            "UPDATE orders SET status=6,cancel_reason=?,cancel_time=?,version=version+1 WHERE id=?",
            r.get("reason"),
            now(),
            o.get("id"));
        db.update(
            "UPDATE delivery_task SET state='CANCELLED',version=version+1 WHERE order_id=?",
            o.get("id"));
      }
      db.update(
          "UPDATE aftersale SET state='APPROVED',decision=?,version=version+1 WHERE id=?",
          input.reason(),
          id);
      db.insert(
          "INSERT INTO"
              + " refund_request(order_id,aftersale_id,store_id,amount_cents,next_attempt_at,created_at)"
              + " VALUES(?,?,?,?,?,?)",
          o.get("id"),
          id,
          o.get("store_id"),
          r.get("amount_cents"),
          now(),
          now());
    }
    scope.audit(
        platform ? "AFTERSALE_RULE" : "AFTERSALE_DECIDE", id, Db.id(o, "store_id"), input.reason());
    scope.event(Db.id(o, "id"), "AFTERSALE_DECIDED");
  }

  private void release(Map<String, Object> r) {
    for (var i :
        db.rows("SELECT * FROM aftersale_item WHERE request_id=? ORDER BY detail_id", r.get("id")))
      db.update(
          "UPDATE order_detail SET reserved_quantity=reserved_quantity-? WHERE id=? AND"
              + " reserved_quantity>=?",
          i.get("quantity"),
          i.get("detail_id"),
          i.get("quantity"));
    db.update(
        "UPDATE orders SET reserved_refund_cents=reserved_refund_cents-? WHERE id=? AND"
            + " reserved_refund_cents>=?",
        r.get("amount_cents"),
        r.get("order_id"),
        r.get("amount_cents"));
  }

  @Transactional
  public void withdraw(long id, long version) {
    Actor.user();
    var initial = request(id, false);
    scope.order(Db.id(initial, "order_id"), true);
    var r = request(id, true);
    require(
        r.get("state").equals("PENDING") && Db.id(r, "version") == version,
        409,
        "AFTERSALE_STATE",
        "仅待处理申请可撤销");
    release(r);
    db.update("UPDATE aftersale SET state='WITHDRAWN',version=version+1 WHERE id=?", id);
    scope.audit("AFTERSALE_WITHDRAW", id, Db.id(r, "store_id"), "用户撤销申请");
  }

  @Transactional
  public void appeal(long id, long version) {
    Actor.user();
    var initial = request(id, false);
    var o = scope.order(Db.id(initial, "order_id"), true);
    var r = request(id, true);
    require(
        !Db.bool(r, "appealed") && Db.id(r, "version") == version,
        409,
        "APPEAL_STATE",
        "仅可申请一次平台介入");
    boolean rejected = r.get("state").equals("REJECTED");
    require(
        rejected
            || r.get("state").equals("PENDING")
                && !now().isBefore(Db.time(r.get("created_at")).plusHours(48)),
        409,
        "APPEAL_STATE",
        "拒绝或超过48小时未处理时可介入");
    if (rejected) {
      require(
          db.count(
                  "SELECT COUNT(*) FROM aftersale WHERE order_id=? AND id<>? AND state"
                      + " IN('PENDING','ESCALATED','APPROVED')",
                  o.get("id"),
                  id)
              == 0,
          409,
          "AFTERSALE_PENDING",
          "请先处理当前其他申请");
      var lines =
          db
              .rows(
                  "SELECT detail_id,quantity FROM aftersale_item WHERE request_id=? ORDER BY"
                      + " detail_id",
                  id)
              .stream()
              .map(
                  i ->
                      new PlatformCommands.RefundLine(
                          Db.id(i, "detail_id"), Db.integer(i, "quantity")))
              .toList();
      db.update("DELETE FROM aftersale_item WHERE request_id=?", id);
      reserve(id, o, r.get("kind").toString(), lines);
    }
    db.update(
        "UPDATE aftersale SET state='ESCALATED',appealed=true,version=version+1 WHERE id=?", id);
    scope.audit("AFTERSALE_APPEAL", id, Db.id(r, "store_id"), "用户申请平台介入");
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
