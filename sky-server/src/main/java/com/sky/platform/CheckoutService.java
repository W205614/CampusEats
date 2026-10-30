package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.business.*;
import com.sky.common.Hashes;
import com.sky.infra.*;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {
  public record Line(
      long cartId,
      String itemType,
      long itemId,
      String name,
      String image,
      String flavors,
      int quantity,
      long normalCents,
      long activityCents,
      long paidCents) {}

  public record Part(
      long storeId,
      long campusId,
      String storeName,
      String campusName,
      long cartVersion,
      String fulfillment,
      Long addressId,
      Map<String, Object> address,
      List<Line> items,
      long subtotalCents,
      long shopDiscountCents,
      long platformDiscountCents,
      long deliveryCents,
      long packagingCents,
      long totalCents,
      Long couponId,
      long settingsVersion) {}

  public record Quote(
      List<Part> stores,
      Long platformCouponId,
      long totalCents,
      String businessDate,
      String quoteHash,
      List<Map<String, Object>> rules) {}

  private final Db db;
  private final Json json;
  private final Clock clock;
  private final PlatformScope scope;
  private final StoreCart carts;
  private final StoreCatalog catalog;
  private final CartService validation;
  private final DirectoryService directory;
  private final MarketingService marketing;
  private final AddressService addresses;
  private final QuotaService quotas;
  private final PaymentGateway gateway;
  private final com.sky.finance.LedgerService ledger;

  public CheckoutService(
      Db db,
      Json json,
      Clock clock,
      PlatformScope scope,
      StoreCart carts,
      StoreCatalog catalog,
      CartService validation,
      DirectoryService directory,
      MarketingService marketing,
      AddressService addresses,
      QuotaService quotas,
      PaymentGateway gateway,
      com.sky.finance.LedgerService ledger) {
    this.db = db;
    this.json = json;
    this.clock = clock;
    this.scope = scope;
    this.carts = carts;
    this.catalog = catalog;
    this.validation = validation;
    this.directory = directory;
    this.marketing = marketing;
    this.addresses = addresses;
    this.quotas = quotas;
    this.gateway = gateway;
    this.ledger = ledger;
  }

  private List<PlatformCommands.Selection> selections(PlatformCommands.Checkout c) {
    return c.stores().stream()
        .sorted(Comparator.comparingLong(PlatformCommands.Selection::storeId))
        .toList();
  }

  @Transactional
  public Quote preview(PlatformCommands.Checkout input) {
    return prepare(Actor.user().id(), input, now());
  }

  private Quote prepare(long user, PlatformCommands.Checkout input, LocalDateTime at) {
    db.one("SELECT id FROM user WHERE id=? FOR UPDATE", user);
    var parts = new ArrayList<Part>();
    var rules = new ArrayList<Map<String, Object>>();
    Set<Long> stores = new HashSet<>(), coupons = new HashSet<>();
    for (var selection : selections(input)) {
      long store = selection.storeId();
      require(stores.add(store), 400, "DUPLICATE_STORE", "同一店铺不能重复结算");
      var s = scope.store(store, true);
      require(directory.accepting(s, at), 409, "SHOP_CLOSED", "店铺当前不接单：" + s.get("name"));
      long version = carts.lock(user, store);
      require(version == selection.cartVersion(), 409, "CART_CHANGED", "店铺购物车已变化");
      Map<String, Object> address = Map.of();
      if (selection.fulfillment().equals("DELIVERY")) {
        require(selection.addressId() != null, 400, "ADDRESS_REQUIRED", "配送订单须选择地址");
        address = addresses.resolve(user, selection.addressId(), true);
        var b =
            db.one("SELECT campus_id FROM campus_building WHERE id=?", address.get("building_id"));
        require(
            Db.id(b, "campus_id") == Db.id(s, "campus_id"),
            409,
            "OUTSIDE_DELIVERY",
            "收货地址与店铺不在同一校区");
      } else require(selection.addressId() == null, 400, "INVALID_ADDRESS", "自取订单不应携带配送地址");
      var raw = carts.items(user, store);
      require(!raw.isEmpty(), 409, "EMPTY_CART", "参与结算的店铺购物车为空");
      var lines = new ArrayList<Line>();
      long subtotal = 0;
      int count = 0;
      for (var item : raw) {
        String type = item.get("item_type").toString();
        long id = Db.id(item, "item_id");
        @SuppressWarnings("unchecked")
        var flavor = (Map<String, String>) item.get("flavors");
        String canonical = validation.validate(type, id, flavor);
        if (type.equals("SETMEAL"))
          for (var component :
              db.rows("SELECT dish_id FROM setmeal_dish WHERE setmeal_id=? ORDER BY dish_id", id))
            catalog.product(store, "DISH", Db.id(component, "dish_id"), true);
        int quantity = Db.integer(item, "quantity");
        long normal = Money.cents(item.get("price"));
        var activityRule = marketing.activity(store, type, id, at);
        long activity =
            activityRule == null ? normal : Math.min(normal, Db.id(activityRule, "price_cents"));
        if (activityRule != null) rules.add(activityRule);
        subtotal = Math.addExact(subtotal, activity * quantity);
        count += quantity;
        lines.add(
            new Line(
                Db.id(item, "id"),
                type,
                id,
                item.get("name").toString(),
                Objects.toString(item.get("image"), ""),
                canonical,
                quantity,
                normal,
                activity,
                activity * quantity));
      }
      if (selection.couponId() != null)
        require(coupons.add(selection.couponId()), 400, "DUPLICATE_COUPON", "同一张券不能重复使用");
      long shopDiscount = marketing.discount(user, selection.couponId(), store, subtotal, at);
      if (selection.couponId() != null) rules.add(marketing.couponSnapshot(selection.couponId()));
      long[] discounts =
          Money.allocate(shopDiscount, lines.stream().mapToLong(Line::paidCents).toArray());
      var adjusted = new ArrayList<Line>();
      for (int i = 0; i < lines.size(); i++) {
        var l = lines.get(i);
        adjusted.add(
            new Line(
                l.cartId,
                l.itemType,
                l.itemId,
                l.name,
                l.image,
                l.flavors,
                l.quantity,
                l.normalCents,
                l.activityCents,
                l.paidCents - discounts[i]));
      }
      long delivery =
          selection.fulfillment().equals("DELIVERY") ? Money.cents(s.get("delivery_fee")) : 0;
      long packaging = Money.cents(s.get("packaging_fee")) * count;
      parts.add(
          new Part(
              store,
              Db.id(s, "campus_id"),
              s.get("name").toString(),
              s.get("campus_name").toString(),
              version,
              selection.fulfillment(),
              selection.addressId(),
              address,
              adjusted,
              subtotal,
              shopDiscount,
              0,
              delivery,
              packaging,
              subtotal - shopDiscount + delivery + packaging,
              selection.couponId(),
              Db.id(s, "version")));
    }
    if (input.platformCouponId() != null)
      require(coupons.add(input.platformCouponId()), 400, "DUPLICATE_COUPON", "同一张券不能重复使用");
    long available = parts.stream().mapToLong(p -> p.subtotalCents - p.shopDiscountCents).sum();
    long platformDiscount = marketing.discount(user, input.platformCouponId(), null, available, at);
    if (input.platformCouponId() != null)
      rules.add(marketing.couponSnapshot(input.platformCouponId()));
    var flat = parts.stream().flatMap(p -> p.items.stream()).toList();
    long[] discounts =
        Money.allocate(platformDiscount, flat.stream().mapToLong(Line::paidCents).toArray());
    int index = 0;
    var result = new ArrayList<Part>();
    for (var p : parts) {
      var adjusted = new ArrayList<Line>();
      long own = 0;
      for (var l : p.items) {
        long d = discounts[index++];
        own += d;
        adjusted.add(
            new Line(
                l.cartId,
                l.itemType,
                l.itemId,
                l.name,
                l.image,
                l.flavors,
                l.quantity,
                l.normalCents,
                l.activityCents,
                l.paidCents - d));
      }
      result.add(
          new Part(
              p.storeId,
              p.campusId,
              p.storeName,
              p.campusName,
              p.cartVersion,
              p.fulfillment,
              p.addressId,
              p.address,
              adjusted,
              p.subtotalCents,
              p.shopDiscountCents,
              own,
              p.deliveryCents,
              p.packagingCents,
              p.totalCents - own,
              p.couponId,
              p.settingsVersion));
    }
    long total = result.stream().mapToLong(Part::totalCents).sum();
    require(total <= 9999999999L, 400, "AMOUNT_LIMIT", "结算金额超出上限");
    String date = at.toLocalDate().toString();
    String hash =
        Hashes.sha256(
            json.write(
                List.of(
                    user,
                    result,
                    input.platformCouponId() == null ? 0 : input.platformCouponId(),
                    date,
                    rules)));
    return new Quote(result, input.platformCouponId(), total, date, hash, rules);
  }

  @Transactional
  public Map<String, Object> submit(String key, PlatformCommands.Checkout input) {
    long user = Actor.user().id();
    require(key != null && key.matches("[0-9a-fA-F-]{36}"), 400, "INVALID_KEY", "请提供UUID幂等键");
    db.one("SELECT id FROM user WHERE id=? FOR UPDATE", user);
    String requestHash =
        Hashes.sha256(
            json.write(
                new PlatformCommands.Checkout(
                    selections(input),
                    input.platformCouponId(),
                    input.quoteHash(),
                    input.remark())));
    var old = db.one("SELECT * FROM checkout WHERE user_id=? AND request_id=?", user, key);
    if (old != null) {
      require(
          requestHash.equals(old.get("request_hash")), 409, "IDEMPOTENCY_CONFLICT", "同一请求键不能更换内容");
      return detail(Db.id(old, "id"));
    }
    LocalDateTime at = now();
    Quote q = prepare(user, input, at);
    require(q.quoteHash.equals(input.quoteHash()), 409, "QUOTE_CHANGED", "报价已变化，请重新确认");
    long checkout =
        db.insert(
            "INSERT INTO"
                + " checkout(user_id,request_id,request_hash,state,total_cents,expires_at,created_at,quote_json)"
                + " VALUES(?,?,?,'PENDING',?,?,?,?)",
            user,
            key,
            requestHash,
            q.totalCents,
            at.plusMinutes(15),
            at,
            json.write(q));
    marketing.reserve(user, q.platformCouponId, checkout);
    for (var p : q.stores) {
      marketing.reserve(user, p.couponId, checkout);
      Map<String, Object> a = p.address;
      long order =
          db.insert(
              "INSERT INTO"
                  + " orders(number,status,user_id,address_book_id,order_time,amount,remark,phone,address,consignee,request_id,request_hash,business_date,snapshot_source,building_name,room,delivery_fee,packaging_fee,pay_expires_at,store_id,campus_id,checkout_id,fulfillment,shop_discount_cents,platform_discount_cents)"
                  + " VALUES(?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
              "CE-" + checkout + "-" + p.storeId,
              user,
              p.addressId == null ? 0 : p.addressId,
              at,
              Money.decimal(p.totalCents),
              input.remark(),
              a.get("phone"),
              p.fulfillment.equals("PICKUP")
                  ? "到店自取"
                  : a.get("building_name") + " " + a.get("room"),
              a.get("consignee"),
              UUID.randomUUID().toString(),
              requestHash,
              at.toLocalDate(),
              "ORIGINAL",
              a.get("building_name"),
              a.get("room"),
              Money.decimal(p.deliveryCents),
              Money.decimal(p.packagingCents),
              at.plusMinutes(15),
              p.storeId,
              p.campusId,
              checkout,
              p.fulfillment,
              p.shopDiscountCents,
              p.platformDiscountCents);
      var quantities = new TreeMap<Long, Integer>();
      for (var l : p.items) {
        db.update(
            "INSERT INTO"
                + " order_detail(order_id,name,image,dish_id,setmeal_id,dish_flavor,number,amount,line_total_cents)"
                + " VALUES(?,?,?,?,?,?,?,?,?)",
            order,
            l.name,
            l.image,
            l.itemType.equals("DISH") ? l.itemId : null,
            l.itemType.equals("SETMEAL") ? l.itemId : null,
            l.flavors,
            l.quantity,
            Money.decimal(l.activityCents),
            l.paidCents);
        if (l.itemType.equals("DISH")) quantities.merge(l.itemId, l.quantity, Integer::sum);
        else
          for (var c :
              db.rows(
                  "SELECT dish_id,copies FROM setmeal_dish WHERE setmeal_id=? ORDER BY dish_id",
                  l.itemId))
            quantities.merge(
                Db.id(c, "dish_id"), l.quantity * Db.integer(c, "copies"), Integer::sum);
      }
      quotas.reserve(order, at.toLocalDate(), quantities);
      carts.clear(user, p.storeId);
      scope.event(order, "ORDER_SUBMIT");
      scope.audit("ORDER_SUBMIT", order, p.storeId, "创建店铺子订单");
    }
    return detail(checkout);
  }

  public List<Map<String, Object>> list() {
    return list(null);
  }

  public List<Map<String, Object>> list(Long beforeId) {
    return list(beforeId, null);
  }

  public List<Map<String, Object>> list(Long beforeId, Integer limit) {
    int pageLimit = ListLimit.resolve(limit);
    require(beforeId == null || beforeId > 0, 400, "INVALID_CURSOR", "分页游标须为正整数");
    var args = new ArrayList<Object>();
    args.add(Actor.user().id());
    if (beforeId != null) args.add(beforeId);
    args.add(pageLimit);
    return db.rows(
        "SELECT id,state,total_cents,paid_at,created_at,expires_at,version FROM checkout WHERE"
            + " user_id=?"
            + (beforeId == null ? "" : " AND id<?")
            + " ORDER BY id DESC LIMIT ?",
        args.toArray());
  }

  public Map<String, Object> byKey(String key) {
    var c =
        db.one("SELECT id FROM checkout WHERE user_id=? AND request_id=?", Actor.user().id(), key);
    require(c != null, 404, "NOT_FOUND", "结算单不存在");
    return detail(Db.id(c, "id"));
  }

  public Map<String, Object> detail(long id) {
    var c = db.one("SELECT * FROM checkout WHERE id=? AND user_id=?", id, Actor.user().id());
    require(c != null, 404, "NOT_FOUND", "结算单不存在");
    c.remove("request_hash");
    var orders =
        db.rows(
            "SELECT o.*,s.name store_name FROM orders o JOIN store s ON s.id=o.store_id WHERE"
                + " o.checkout_id=? ORDER BY o.store_id",
            id);
    for (var order : orders) {
      order.remove("pickup_hash");
      order.remove("request_hash");
    }
    c.put("orders", orders);
    return c;
  }

  @Transactional
  public Map<String, Object> pay(long id, long version) {
    var c = lockUser(id);
    if (c.get("state").equals("PAID")) return detail(id);
    require(c.get("state").equals("PENDING"), 409, "CHECKOUT_STATE", "结算单不能支付");
    require(Db.id(c, "version") == version, 409, "VERSION_CONFLICT", "结算单已变化");
    if (!now().isBefore(Db.time(c.get("expires_at")))) {
      cancelLocked(c);
      return detail(id);
    }
    String paymentRef =
        Db.id(c, "total_cents") > 0
            ? gateway
                .pay(new PaymentGateway.Payment("checkout:" + id, Db.id(c, "total_cents"), "CNY"))
                .reference()
            : "ZERO-CHECKOUT-" + id;
    db.update("UPDATE checkout SET state='PAID',paid_at=?,version=version+1 WHERE id=?", now(), id);
    for (var o : db.rows("SELECT * FROM orders WHERE checkout_id=? ORDER BY id FOR UPDATE", id)) {
      db.update(
          "UPDATE orders SET"
              + " status=2,pay_status=1,paid_cents=ROUND(amount*100),checkout_time=?,version=version+1"
              + " WHERE id=?",
          now(),
          o.get("id"));
      db.update(
          "INSERT INTO payment_attempt(order_id,gateway_ref,created_at) VALUES(?,?,?)",
          o.get("id"),
          paymentRef + "-" + o.get("id"),
          now());
      scope.event(Db.id(o, "id"), "ORDER_PAID");
      ledger.payment(Db.id(o, "id"));
    }
    db.update("UPDATE user_coupon SET state='USED' WHERE checkout_id=? AND state='LOCKED'", id);
    scope.audit("CHECKOUT_PAY", id, null, "整批模拟支付成功");
    return detail(id);
  }

  @Transactional
  public void cancel(long id, long version) {
    var c = lockUser(id);
    if (c.get("state").equals("CANCELLED")) return;
    require(
        c.get("state").equals("PENDING") && Db.id(c, "version") == version,
        409,
        "CHECKOUT_STATE",
        "仅能取消当前版本的待支付结算单");
    cancelLocked(c);
  }

  private Map<String, Object> lockUser(long id) {
    var c =
        db.one("SELECT * FROM checkout WHERE id=? AND user_id=? FOR UPDATE", id, Actor.user().id());
    require(c != null, 404, "NOT_FOUND", "结算单不存在");
    return c;
  }

  public void cancelLocked(Map<String, Object> c) {
    long id = Db.id(c, "id");
    for (var o : db.rows("SELECT id FROM orders WHERE checkout_id=? ORDER BY id FOR UPDATE", id)) {
      long order = Db.id(o, "id");
      quotas.transition(order, false);
      db.update(
          "UPDATE orders SET status=6,cancel_time=?,cancel_reason='结算取消或超时',version=version+1 WHERE"
              + " id=?",
          now(),
          order);
      scope.event(order, "ORDER_CANCELLED");
    }
    db.update(
        "UPDATE user_coupon SET state='AVAILABLE',checkout_id=NULL WHERE checkout_id=? AND"
            + " state='LOCKED'",
        id);
    db.update("UPDATE checkout SET state='CANCELLED',version=version+1 WHERE id=?", id);
  }

  @Transactional
  public void expire(long id) {
    var c = db.one("SELECT * FROM checkout WHERE id=? FOR UPDATE", id);
    if (c != null
        && c.get("state").equals("PENDING")
        && !now().isBefore(Db.time(c.get("expires_at")))) cancelLocked(c);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
  }
}
