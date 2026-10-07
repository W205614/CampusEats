package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.*;
import com.sky.common.Hashes;
import com.sky.infra.*;
import com.sky.security.Actor;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {
  private final MockPaymentGateway gateway;
  private final Db db;
  private final Json json;
  private final CartService cart;
  private final PricingService pricing;
  private final QuotaService quotas;
  private final OrderEvents events;
  private final Audit audit;
  private final Clock clock;

  public OrderCommandService(
      MockPaymentGateway gateway,
      Db db,
      Json json,
      CartService cart,
      PricingService pricing,
      QuotaService quotas,
      OrderEvents events,
      Audit audit,
      Clock clock) {
    this.gateway = gateway;
    this.db = db;
    this.json = json;
    this.cart = cart;
    this.pricing = pricing;
    this.quotas = quotas;
    this.events = events;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  public Quote preview(long user, Preview input) {
    long v = cart.lock(user);
    cart.version(v, input.cartVersion());
    return pricing.prepare(user, input.addressId(), v, false).quote();
  }

  @Transactional
  public Map<String, Object> submit(long user, String key, Submit input) {
    try {
      require(
          UUID.fromString(key).toString().equals(key), 400, "INVALID_REQUEST_KEY", "请求键必须为标准UUID");
    } catch (IllegalArgumentException ex) {
      throw new com.sky.common.BusinessException(400, "INVALID_REQUEST_KEY", "请求键必须为标准UUID");
    }
    String hash = Hashes.sha256(json.write(input));
    long version = cart.lock(user);
    var previous = db.one("SELECT * FROM orders WHERE user_id=? AND request_id=?", user, key);
    if (previous != null) {
      require(
          hash.equals(previous.get("request_hash")), 409, "IDEMPOTENCY_CONFLICT", "同一个请求键不能用于不同订单");
      return previous;
    }
    cart.version(version, input.cartVersion());
    var prepared = pricing.prepare(user, input.addressId(), version, true);
    var quote = prepared.quote();
    require(quote.quoteHash().equals(input.quoteHash()), 409, "QUOTE_CHANGED", "商品、费用或地址已变化，请重新确认");
    var address = prepared.address();
    LocalDateTime now = LocalDateTime.now(clock);
    String number = "CE" + UUID.randomUUID().toString().replace("-", "");
    long order =
        db.insert(
            "INSERT INTO"
                + " orders(number,status,user_id,address_book_id,order_time,amount,remark,phone,address,consignee,request_id,request_hash,business_date,snapshot_source,building_name,room,delivery_fee,packaging_fee,pay_expires_at,pack_amount)"
                + " VALUES(?,1,?,?,?,?,?,?,?,?,?,?,?,'ORIGINAL',?,?,?,?,?,?)",
            number,
            user,
            input.addressId(),
            now,
            new BigDecimal(quote.total()),
            input.remark(),
            address.get("phone"),
            address.get("building_name") + " " + address.get("room"),
            address.get("consignee"),
            key,
            hash,
            LocalDate.parse(quote.businessDate()),
            address.get("building_name"),
            address.get("room"),
            new BigDecimal(quote.deliveryFee()),
            new BigDecimal(quote.packagingFee()),
            now.plusMinutes(15),
            new BigDecimal(quote.packagingFee()).intValue());
    for (var item : prepared.items())
      db.update(
          "INSERT INTO"
              + " order_detail(name,image,order_id,dish_id,setmeal_id,dish_flavor,number,amount)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          item.get("name"),
          item.get("image"),
          order,
          item.get("item_type").equals("DISH") ? item.get("item_id") : null,
          item.get("item_type").equals("SETMEAL") ? item.get("item_id") : null,
          json.write(item.get("flavors")),
          item.get("quantity"),
          item.get("price"));
    cart.clearLocked(user);
    audit.write(new Actor(user, "USER", "USER", "", false), "ORDER_SUBMIT", order, "创建订单");
    events.append("ORDER_CREATED", order);
    var result = db.one("SELECT * FROM orders WHERE id=?", order);
    // Acquire shared daily stock rows last; rollback still includes every preceding write.
    quotas.reserve(order, LocalDate.parse(quote.businessDate()), prepared.quantities());
    return result;
  }

  @Transactional
  public Map<String, Object> pay(long user, long id, long version) {
    var row = owned(id, user);
    int status = Db.integer(row, "status");
    if (Db.integer(row, "pay_status") != 0) return row;
    require(status == 1, 409, "ORDER_STATE", "当前订单不能支付");
    require(
        Db.time(row.get("pay_expires_at")).isAfter(LocalDateTime.now(clock)),
        409,
        "PAYMENT_EXPIRED",
        "支付时间已过期");
    expected(row, version);
    String gatewayRef = gateway.pay(id);
    require(
        db.update(
                "UPDATE orders SET status=2,pay_status=1,checkout_time=?,version=version+1 WHERE"
                    + " id=? AND user_id=? AND status=1 AND pay_status=0 AND version=?",
                LocalDateTime.now(clock),
                id,
                user,
                version)
            == 1,
        409,
        "ORDER_CHANGED",
        "订单状态已变化");
    db.update(
        "INSERT INTO payment_attempt(order_id,gateway_ref,created_at) VALUES(?,?,?)",
        id,
        gatewayRef,
        LocalDateTime.now(clock));
    events.append("ORDER_PAID", id);
    audit.write(new Actor(user, "USER", "USER", "", false), "MOCK_PAYMENT", id, "模拟支付成功");
    return db.one("SELECT * FROM orders WHERE id=?", id);
  }

  @Transactional
  public Map<String, Object> action(Actor actor, long id, String action, OrderAction input) {
    var row = actor.type().equals("USER") ? owned(id, actor.id()) : locked(id);
    if (actor.type().equals("USER")) require(action.equals("cancel"), 403, "FORBIDDEN", "没有操作权限");
    if (actor.role().equals("DELIVERER"))
      require(
          (action.equals("dispatch") || action.equals("complete"))
              && row.get("courier_id") != null
              && Db.id(row, "courier_id") == actor.id(),
          403,
          "FORBIDDEN",
          "仅能操作自己的配送订单");
    int status = Db.integer(row, "status");
    if (status == 6 && action.equals("cancel")) return row;
    expected(row, input.version());
    int next;
    Boolean consumeQuota = null;
    switch (action) {
      case "accept" -> {
        require(
            actor.type().equals("ADMIN") && !actor.role().equals("DELIVERER") && status == 2,
            409,
            "ORDER_STATE",
            "当前不能接单");
        next = 3;
        consumeQuota = true;
      }
      case "reject" -> {
        require(
            actor.type().equals("ADMIN") && !actor.role().equals("DELIVERER") && status == 2,
            409,
            "ORDER_STATE",
            "当前不能拒单");
        next = 6;
        consumeQuota = false;
      }
      case "cancel" -> {
        require(
            status == 1
                || status == 2
                || (actor.type().equals("ADMIN")
                    && !actor.role().equals("DELIVERER")
                    && status == 3),
            409,
            "ORDER_STATE",
            "当前不能取消");
        next = 6;
        if (status < 3) consumeQuota = false;
      }
      case "assign" -> {
        require(
            actor.type().equals("ADMIN")
                && !actor.role().equals("DELIVERER")
                && status == 3
                && input.courierId() != null,
            409,
            "ORDER_STATE",
            "接单后才能派单");
        var courier =
            db.one(
                "SELECT id FROM employee WHERE id=? AND role='DELIVERER' AND status=1",
                input.courierId());
        require(courier != null, 400, "INVALID_COURIER", "请选择启用的配送员");
        next = 3;
      }
      case "dispatch" -> {
        require(
            status == 3
                && row.get("courier_id") != null
                && (actor.role().equals("ADMIN") || actor.role().equals("DELIVERER")),
            409,
            "ORDER_STATE",
            "派单后由配送员开始配送");
        next = 4;
      }
      case "complete" -> {
        require(
            status == 4 && (actor.role().equals("ADMIN") || actor.role().equals("DELIVERER")),
            409,
            "ORDER_STATE",
            "配送中才能确认送达");
        next = 5;
      }
      default -> throw new com.sky.common.BusinessException(400, "INVALID_ACTION", "订单操作不正确");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    require(
        db.update(
                "UPDATE orders SET"
                    + " status=?,version=version+1,courier_id=COALESCE(?,courier_id),delivery_started_at=CASE"
                    + " WHEN ?='dispatch' THEN ? ELSE delivery_started_at END,delivery_time=CASE"
                    + " WHEN ?='complete' THEN ? ELSE delivery_time END,cancel_time=CASE WHEN ?=6"
                    + " THEN ? ELSE cancel_time END,cancel_reason=CASE WHEN ?='cancel' THEN ? ELSE"
                    + " cancel_reason END,rejection_reason=CASE WHEN ?='reject' THEN ? ELSE"
                    + " rejection_reason END WHERE id=? AND version=? AND status=?",
                next,
                action.equals("assign") ? input.courierId() : null,
                action,
                now,
                action,
                now,
                next,
                now,
                action,
                input.reason(),
                action,
                input.reason(),
                id,
                input.version(),
                status)
            == 1,
        409,
        "ORDER_CHANGED",
        "订单状态已变化");
    if (next == 6 && Db.integer(row, "pay_status") == 1)
      db.update(
          "INSERT IGNORE INTO refund_task(order_id,next_attempt_at,created_at) VALUES(?,?,?)",
          id,
          now,
          now);
    events.append("ORDER_" + action.toUpperCase(Locale.ROOT), id);
    audit.write(
        actor, "ORDER_" + action.toUpperCase(Locale.ROOT), id, "状态 " + status + " -> " + next);
    var result = db.one("SELECT * FROM orders WHERE id=?", id);
    if (consumeQuota != null) quotas.transition(id, consumeQuota);
    return result;
  }

  @Transactional
  public void expire(long id) {
    var row = locked(id);
    if (Db.integer(row, "status") != 1
        || row.get("pay_expires_at") == null
        || Db.time(row.get("pay_expires_at")).isAfter(LocalDateTime.now(clock))) return;
    db.update(
        "UPDATE orders SET status=6,cancel_reason='支付超时',cancel_time=?,version=version+1 WHERE id=?"
            + " AND status=1",
        LocalDateTime.now(clock),
        id);
    events.append("ORDER_EXPIRED", id);
    audit.write(null, "ORDER_EXPIRED", id, "支付超时取消");
    quotas.transition(id, false);
  }

  @Transactional
  public void reminder(long user, long id) {
    var row = owned(id, user);
    require(
        Db.integer(row, "status") >= 2 && Db.integer(row, "status") <= 4,
        409,
        "ORDER_STATE",
        "当前订单不能催单");
    events.append("ORDER_REMINDER", id);
    audit.write(new Actor(user, "USER", "USER", "", false), "ORDER_REMINDER", id, "用户催单");
  }

  @Transactional
  public void markOverdue(long id) {
    if (db.update(
            "UPDATE orders SET delivery_overdue=true,version=version+1 WHERE id=? AND status=4 AND"
                + " delivery_started_at<? AND delivery_overdue=false",
            id,
            LocalDateTime.now(clock).minusMinutes(60))
        == 1) {
      events.append("ORDER_OVERDUE", id);
      audit.write(null, "ORDER_OVERDUE", id, "配送超过60分钟，等待人工处理");
    }
  }

  private Map<String, Object> owned(long id, long user) {
    var row = db.one("SELECT * FROM orders WHERE id=? AND user_id=? FOR UPDATE", id, user);
    require(row != null, 404, "NOT_FOUND", "订单不存在");
    return row;
  }

  private Map<String, Object> locked(long id) {
    var row = db.one("SELECT * FROM orders WHERE id=? FOR UPDATE", id);
    require(row != null, 404, "NOT_FOUND", "订单不存在");
    return row;
  }

  private void expected(Map<String, Object> row, long version) {
    require(Db.id(row, "version") == version, 409, "ORDER_CHANGED", "订单状态已变化，请刷新");
  }
}
