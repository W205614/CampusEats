package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.*;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketingService {
  private final Db db;
  private final PlatformScope scope;
  private final StoreCatalog catalog;
  private final Clock clock;

  public MarketingService(Db db, PlatformScope scope, StoreCatalog catalog, Clock clock) {
    this.db = db;
    this.scope = scope;
    this.catalog = catalog;
    this.clock = clock;
  }

  public List<Map<String, Object>> coupons(Long store, boolean admin) {
    if (admin) {
      if (store == null) scope.platform();
      else scope.merchant(store, true);
    }
    var args = new ArrayList<Object>();
    if (store != null) args.add(store);
    if (!admin) {
      args.add(now());
      args.add(now());
    }
    return db.rows(
        "SELECT * FROM coupon WHERE "
            + (store == null ? "store_id IS NULL" : "store_id=?")
            + (admin ? "" : " AND enabled=true AND starts_at<=? AND ends_at>?")
            + " ORDER BY id DESC",
        args.toArray());
  }

  @Transactional
  public long coupon(Long store, Long id, PlatformCommands.Coupon input) {
    if (store == null) scope.platform();
    else scope.merchant(store, true);
    require(input.endsAt().isAfter(input.startsAt()), 400, "INVALID_TIME", "结束须晚于开始");
    require(
        input.value() <= 999999999 && (input.kind().equals("REDUCTION") || input.value() < 10000),
        400,
        "INVALID_DISCOUNT",
        "折扣使用1至9999基点，例如8000代表八折");
    if (id != null) {
      var old = db.one("SELECT * FROM coupon WHERE id=? FOR UPDATE", id);
      require(
          old != null
              && Objects.equals(old.get("store_id") == null ? null : Db.id(old, "store_id"), store),
          404,
          "NOT_FOUND",
          "优惠券不存在");
      require(Db.integer(old, "claimed") == 0, 409, "COUPON_IMMUTABLE", "已领取券规则不可修改，可使用停发操作");
      db.update("DELETE FROM coupon WHERE id=?", id);
    }
    long target =
        db.insert(
            "INSERT INTO coupon("
                + (id == null ? "" : "id,")
                + "store_id,name,kind,threshold_cents,value,cap_cents,total,per_user,starts_at,ends_at,enabled)"
                + " VALUES("
                + (id == null ? "" : "?,")
                + "?,?,?,?,?,?,?,?,?,?,?)",
            couponArgs(id, store, input));
    scope.audit("COUPON_SAVE", target, store, "更新优惠券");
    return target;
  }

  private Object[] couponArgs(Long id, Long store, PlatformCommands.Coupon c) {
    var a = new ArrayList<Object>();
    if (id != null) a.add(id);
    Collections.addAll(
        a,
        store,
        c.name(),
        c.kind(),
        c.thresholdCents(),
        c.value(),
        c.capCents(),
        c.total(),
        c.perUser(),
        c.startsAt(),
        c.endsAt(),
        c.enabled());
    return a.toArray();
  }

  @Transactional
  public void stop(Long store, long id) {
    if (store == null) scope.platform();
    else scope.merchant(store, true);
    var row = db.one("SELECT * FROM coupon WHERE id=? FOR UPDATE", id);
    require(
        row != null
            && Objects.equals(row.get("store_id") == null ? null : Db.id(row, "store_id"), store),
        404,
        "NOT_FOUND",
        "优惠券不存在");
    db.update("UPDATE coupon SET enabled=false WHERE id=?", id);
    scope.audit("COUPON_STOP", id, store, "停止发券");
  }

  @Transactional
  public long claim(long id) {
    long user = Actor.user().id();
    var c = db.one("SELECT * FROM coupon WHERE id=? FOR UPDATE", id);
    require(
        c != null
            && Db.bool(c, "enabled")
            && !now().isBefore(Db.time(c.get("starts_at")))
            && now().isBefore(Db.time(c.get("ends_at"))),
        409,
        "COUPON_UNAVAILABLE",
        "优惠券当前不可领取");
    if (c.get("store_id") != null) scope.store(Db.id(c, "store_id"), true);
    require(
        db.count("SELECT COUNT(*) FROM user_coupon WHERE user_id=? AND coupon_id=?", user, id)
            < Db.integer(c, "per_user"),
        409,
        "CLAIM_LIMIT",
        "已达到个人领取上限");
    require(
        db.update("UPDATE coupon SET claimed=claimed+1 WHERE id=? AND claimed<total", id) == 1,
        409,
        "COUPON_SOLD_OUT",
        "优惠券已领完");
    return db.insert("INSERT INTO user_coupon(user_id,coupon_id) VALUES(?,?)", user, id);
  }

  public List<Map<String, Object>> wallet() {
    return db.rows(
        "SELECT u.id,u.state,u.checkout_id,c.id"
            + " coupon_template_id,c.store_id,c.name,c.kind,c.threshold_cents,c.value,c.cap_cents,c.starts_at,c.ends_at"
            + " FROM user_coupon u JOIN coupon c ON c.id=u.coupon_id WHERE u.user_id=? ORDER BY"
            + " u.id DESC",
        Actor.user().id());
  }

  public long discount(long user, Long ownedCoupon, Long store, long subtotal) {
    return discount(user, ownedCoupon, store, subtotal, now());
  }

  public long discount(long user, Long ownedCoupon, Long store, long subtotal, LocalDateTime at) {
    if (ownedCoupon == null) return 0;
    var c =
        db.one(
            "SELECT u.state,u.user_id,c.* FROM user_coupon u JOIN coupon c ON c.id=u.coupon_id"
                + " WHERE u.id=? FOR UPDATE",
            ownedCoupon);
    require(
        c != null
            && Db.id(c, "user_id") == user
            && c.get("state").equals("AVAILABLE")
            && Objects.equals(c.get("store_id") == null ? null : Db.id(c, "store_id"), store),
        409,
        "COUPON_UNAVAILABLE",
        "请选择本人可用的对应优惠券");
    require(
        !at.isBefore(Db.time(c.get("starts_at")))
            && at.isBefore(Db.time(c.get("ends_at")))
            && subtotal >= Db.id(c, "threshold_cents"),
        409,
        "COUPON_THRESHOLD",
        "优惠券过期或未满足门槛");
    long discount =
        c.get("kind").equals("REDUCTION")
            ? Db.id(c, "value")
            : subtotal - subtotal * Db.id(c, "value") / 10000;
    if (c.get("cap_cents") != null) discount = Math.min(discount, Db.id(c, "cap_cents"));
    return Math.min(subtotal, discount);
  }

  public void reserve(long user, Long coupon, long checkout) {
    if (coupon != null)
      require(
          db.update(
                  "UPDATE user_coupon SET state='LOCKED',checkout_id=? WHERE id=? AND user_id=? AND"
                      + " state='AVAILABLE'",
                  checkout,
                  coupon,
                  user)
              == 1,
          409,
          "COUPON_UNAVAILABLE",
          "优惠券已被使用");
  }

  public List<Map<String, Object>> promotions(long store) {
    scope.merchant(store, true);
    return db.rows("SELECT * FROM promotion WHERE store_id=? ORDER BY id DESC", store);
  }

  @Transactional
  public long promotion(long store, Long id, PlatformCommands.Promotion input) {
    scope.merchant(store, true);
    String table = PlatformScope.table(input.itemType());
    db.one(
        "SELECT id FROM " + table + " WHERE id=? AND store_id=? FOR UPDATE", input.itemId(), store);
    var p = catalog.product(store, input.itemType(), input.itemId(), true);
    require(
        input.endsAt().isAfter(input.startsAt())
            && input.priceCents() <= Money.cents(p.get("price")),
        400,
        "INVALID_PROMOTION",
        "活动时间或价格不正确");
    if (id != null)
      require(
          db.count(
                  "SELECT COUNT(*) FROM promotion WHERE id=? AND store_id=? AND item_type=? AND"
                      + " item_id=?",
                  id,
                  store,
                  input.itemType(),
                  input.itemId())
              == 1,
          404,
          "NOT_FOUND",
          "活动不存在或商品不能更换");
    require(
        !input.enabled()
            || db.count(
                    "SELECT COUNT(*) FROM promotion WHERE store_id=? AND item_type=? AND item_id=?"
                        + " AND enabled=true AND starts_at<? AND ends_at>? AND id<>?",
                    store,
                    input.itemType(),
                    input.itemId(),
                    input.endsAt(),
                    input.startsAt(),
                    id == null ? 0 : id)
                == 0,
        409,
        "PROMOTION_OVERLAP",
        "同商品活动时间不能重叠");
    long target =
        id == null
            ? db.insert(
                "INSERT INTO"
                    + " promotion(store_id,item_type,item_id,price_cents,starts_at,ends_at,enabled)"
                    + " VALUES(?,?,?,?,?,?,?)",
                store,
                input.itemType(),
                input.itemId(),
                input.priceCents(),
                input.startsAt(),
                input.endsAt(),
                input.enabled())
            : id;
    if (id != null)
      db.update(
          "UPDATE promotion SET price_cents=?,starts_at=?,ends_at=?,enabled=? WHERE id=?",
          input.priceCents(),
          input.startsAt(),
          input.endsAt(),
          input.enabled(),
          id);
    scope.audit("PROMOTION_SAVE", target, store, "更新限时活动价");
    return target;
  }

  public long price(long store, String type, long item, long normal) {
    var p =
        db.one(
            "SELECT price_cents FROM promotion WHERE store_id=? AND item_type=? AND item_id=? AND"
                + " enabled=true AND starts_at<=? AND ends_at>? FOR SHARE",
            store,
            type,
            item,
            now(),
            now());
    return p == null ? normal : Math.min(normal, Db.id(p, "price_cents"));
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public Map<String, Object> couponSnapshot(long owned) {
    return db.one(
        "SELECT"
            + " c.id,c.store_id,c.name,c.kind,c.threshold_cents,c.value,c.cap_cents,c.starts_at,c.ends_at"
            + " FROM user_coupon u JOIN coupon c ON c.id=u.coupon_id WHERE u.id=?",
        owned);
  }

  public Map<String, Object> activity(long store, String type, long item) {
    return activity(store, type, item, now());
  }

  public Map<String, Object> activity(long store, String type, long item, LocalDateTime at) {
    return db.one(
        "SELECT id,store_id,item_type,item_id,price_cents,starts_at,ends_at FROM promotion WHERE"
            + " store_id=? AND item_type=? AND item_id=? AND enabled=true AND starts_at<=? AND"
            + " ends_at>? FOR SHARE",
        store,
        type,
        item,
        at,
        at);
  }
}
