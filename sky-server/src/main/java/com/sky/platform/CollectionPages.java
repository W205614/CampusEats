package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CollectionPages {
  @io.swagger.v3.oas.annotations.media.Schema(name = "CollectionStaff")
  public record Staff(
      String id,
      String username,
      String name,
      String role,
      String storeId,
      String campusId,
      int status,
      boolean mustChangePassword,
      boolean acceptingTasks) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "CollectionCoupon")
  public record Coupon(
      String id,
      String storeId,
      String name,
      String kind,
      String thresholdCents,
      String value,
      String capCents,
      int total,
      int claimed,
      int perUser,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      boolean enabled) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "CollectionWallet")
  public record Wallet(
      String id,
      String state,
      String checkoutId,
      String couponTemplateId,
      String storeId,
      String name,
      String kind,
      String thresholdCents,
      String value,
      String capCents,
      LocalDateTime startsAt,
      LocalDateTime endsAt) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "CollectionPromotion")
  public record Promotion(
      String id,
      String storeId,
      String itemType,
      String itemId,
      String priceCents,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      boolean enabled) {}

  @io.swagger.v3.oas.annotations.media.Schema(name = "CollectionFavorite")
  public record Favorite(
      String targetType, String targetId, String storeId, String storeName, String name) {}

  private final Db db;
  private final PlatformScope scope;
  private final CursorPages pages;
  private final Clock clock;

  public CollectionPages(Db db, PlatformScope scope, CursorPages pages, Clock clock) {
    this.db = db;
    this.scope = scope;
    this.pages = pages;
    this.clock = clock;
  }

  private static String s(Map<String, Object> r, String key) {
    return r.get(key) == null ? null : r.get(key).toString();
  }

  private void state(String value, String... choices) {
    require(value == null || Set.of(choices).contains(value), 400, "INVALID_STATE", "筛选状态不正确");
  }

  private void search(String query, String column, StringBuilder filter, List<Object> args) {
    if (!query.isBlank()) {
      filter.append(" AND LOCATE(CAST(? AS BINARY),CAST(").append(column).append(" AS BINARY))>0");
      args.add(query);
    }
  }

  public CursorPages.Page<Staff> staff(Long store, CursorPages.Request q) {
    if (store == null) scope.platform();
    else scope.merchant(store, true);
    state(q.state(), "ENABLED", "DISABLED");
    String text = q.text();
    String range = pages.scope("staff", store, q.state(), text);
    var filter = new StringBuilder(" WHERE 1=1");
    var args = new ArrayList<Object>();
    if (store != null) {
      filter.append(" AND store_id=?");
      args.add(store);
    }
    if (q.state() != null) {
      filter.append(" AND status=?");
      args.add(q.state().equals("ENABLED") ? 1 : 0);
    }
    search(text, "CONCAT(username,' ',name)", filter, args);
    args.add(pages.before(q.cursor(), range));
    int size = q.size();
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT"
                + " id,username,name,role,store_id,campus_id,status,must_change_password,accepting_tasks"
                + " FROM employee"
                + filter
                + " AND id<? ORDER BY id DESC LIMIT ?",
            args.toArray());
    return pages.finish(
        rows.stream()
            .map(
                r ->
                    new Staff(
                        s(r, "id"),
                        s(r, "username"),
                        s(r, "name"),
                        s(r, "role"),
                        s(r, "store_id"),
                        s(r, "campus_id"),
                        Db.integer(r, "status"),
                        Db.bool(r, "must_change_password"),
                        Db.bool(r, "accepting_tasks")))
            .toList(),
        size,
        range,
        r -> Long.parseLong(r.id()));
  }

  public CursorPages.Page<Coupon> coupons(Long store, boolean admin, CursorPages.Request q) {
    if (admin) {
      if (store == null) scope.platform();
      else scope.merchant(store, true);
    } else {
      Actor.user();
      if (store != null) scope.store(store, true);
    }
    state(q.state(), "ENABLED", "DISABLED");
    String text = q.text();
    String range = pages.scope("coupons", store, admin, q.state(), text);
    var filter = new StringBuilder(store == null ? " WHERE store_id IS NULL" : " WHERE store_id=?");
    var args = new ArrayList<Object>();
    if (store != null) args.add(store);
    if (!admin) {
      filter.append(" AND enabled=true AND starts_at<=? AND ends_at>?");
      var at = LocalDateTime.now(clock);
      args.add(at);
      args.add(at);
    }
    if (q.state() != null) {
      filter.append(" AND enabled=?");
      args.add(q.state().equals("ENABLED"));
    }
    search(text, "name", filter, args);
    args.add(pages.before(q.cursor(), range));
    int size = q.size();
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT * FROM coupon" + filter + " AND id<? ORDER BY id DESC LIMIT ?", args.toArray());
    return pages.finish(
        rows.stream()
            .map(
                r ->
                    new Coupon(
                        s(r, "id"),
                        s(r, "store_id"),
                        s(r, "name"),
                        s(r, "kind"),
                        s(r, "threshold_cents"),
                        s(r, "value"),
                        s(r, "cap_cents"),
                        Db.integer(r, "total"),
                        Db.integer(r, "claimed"),
                        Db.integer(r, "per_user"),
                        Db.time(r.get("starts_at")),
                        Db.time(r.get("ends_at")),
                        Db.bool(r, "enabled")))
            .toList(),
        size,
        range,
        r -> Long.parseLong(r.id()));
  }

  public CursorPages.Page<Wallet> wallet(CursorPages.Request q) {
    long user = Actor.user().id();
    var at = LocalDateTime.now(clock);
    state(q.state(), "AVAILABLE", "LOCKED", "USED", "EXPIRED");
    String text = q.text();
    String range = pages.scope("wallet", q.state(), text);
    var filter = new StringBuilder(" WHERE u.user_id=?");
    var args = new ArrayList<Object>();
    args.add(user);
    if (q.state() != null) {
      if (q.state().equals("EXPIRED")) {
        filter.append(" AND u.state='AVAILABLE' AND c.ends_at<=?");
        args.add(at);
      } else {
        filter.append(" AND u.state=?");
        args.add(q.state());
        if (q.state().equals("AVAILABLE")) {
          filter.append(" AND c.ends_at>?");
          args.add(at);
        }
      }
    }
    search(text, "c.name", filter, args);
    args.add(pages.before(q.cursor(), range));
    int size = q.size();
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT u.id,u.state,u.checkout_id,c.id"
                + " coupon_template_id,c.store_id,c.name,c.kind,c.threshold_cents,c.value,c.cap_cents,c.starts_at,c.ends_at"
                + " FROM user_coupon u JOIN coupon c ON c.id=u.coupon_id"
                + filter
                + " AND u.id<? ORDER BY u.id DESC LIMIT ?",
            args.toArray());
    return pages.finish(
        rows.stream()
            .map(
                r ->
                    new Wallet(
                        s(r, "id"),
                        WalletState.display(s(r, "state"), Db.time(r.get("ends_at")), at),
                        s(r, "checkout_id"),
                        s(r, "coupon_template_id"),
                        s(r, "store_id"),
                        s(r, "name"),
                        s(r, "kind"),
                        s(r, "threshold_cents"),
                        s(r, "value"),
                        s(r, "cap_cents"),
                        Db.time(r.get("starts_at")),
                        Db.time(r.get("ends_at"))))
            .toList(),
        size,
        range,
        r -> Long.parseLong(r.id()));
  }

  public CursorPages.Page<Promotion> promotions(long store, CursorPages.Request q) {
    scope.merchant(store, true);
    state(q.state(), "ENABLED", "DISABLED");
    String text = q.text();
    String range = pages.scope("promotions", store, q.state(), text);
    var filter = new StringBuilder(" WHERE store_id=?");
    var args = new ArrayList<Object>();
    args.add(store);
    if (q.state() != null) {
      filter.append(" AND enabled=?");
      args.add(q.state().equals("ENABLED"));
    }
    // Item type is a controlled filter; product text lookup stays inside the same store.
    if (!text.isBlank()) {
      filter.append(
          " AND ((item_type='DISH' AND EXISTS(SELECT 1 FROM dish d WHERE d.id=promotion.item_id AND"
              + " d.store_id=promotion.store_id AND LOCATE(?,d.name)>0)) OR (item_type='SETMEAL'"
              + " AND EXISTS(SELECT 1 FROM setmeal m WHERE m.id=promotion.item_id AND"
              + " m.store_id=promotion.store_id AND LOCATE(?,m.name)>0)))");
      args.add(text);
      args.add(text);
    }
    args.add(pages.before(q.cursor(), range));
    int size = q.size();
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT * FROM promotion" + filter + " AND id<? ORDER BY id DESC LIMIT ?",
            args.toArray());
    return pages.finish(
        rows.stream()
            .map(
                r ->
                    new Promotion(
                        s(r, "id"),
                        s(r, "store_id"),
                        s(r, "item_type"),
                        s(r, "item_id"),
                        s(r, "price_cents"),
                        Db.time(r.get("starts_at")),
                        Db.time(r.get("ends_at")),
                        Db.bool(r, "enabled")))
            .toList(),
        size,
        range,
        r -> Long.parseLong(r.id()));
  }

  public CursorPages.Page<Favorite> favorites(CursorPages.Request q) {
    Actor.user();
    require(q.state() == null, 400, "INVALID_STATE", "收藏不支持状态筛选");
    String text = q.text();
    String range = pages.scope("favorites", text);
    var pos = pages.position(q.cursor(), range);
    String afterType = pos.rank() == 0 ? "DISH" : pos.rank() == 1 ? "SETMEAL" : "STORE";
    long afterId = pos.id();
    var args = new ArrayList<Object>();
    args.add(Actor.user().id());
    String filter = "";
    if (q.cursor() != null) {
      filter +=
          " AND (f.store_id>? OR (f.store_id=? AND (f.target_type>? OR (f.target_type=? AND"
              + " f.target_id>?))))";
      Collections.addAll(args, pos.storeId(), pos.storeId(), afterType, afterType, afterId);
    }
    if (!text.isBlank()) {
      filter +=
          " AND LOCATE(?,COALESCE(CASE WHEN f.target_type='STORE' THEN s.name WHEN"
              + " f.target_type='DISH' THEN d.name ELSE m.name END,''))>0";
      args.add(text);
    }
    int size = q.size();
    args.add(size + 1);
    var rows =
        db.rows(
            "SELECT f.*,s.name store_name,CASE WHEN f.target_type='STORE' THEN s.name WHEN"
                + " f.target_type='DISH' THEN d.name ELSE m.name END name FROM favorite f JOIN"
                + " store s ON s.id=f.store_id LEFT JOIN dish d ON f.target_type='DISH' AND"
                + " d.id=f.target_id AND d.store_id=f.store_id LEFT JOIN setmeal m ON"
                + " f.target_type='SETMEAL' AND m.id=f.target_id AND m.store_id=f.store_id WHERE"
                + " f.user_id=?"
                + filter
                + " ORDER BY f.store_id,f.target_type,f.target_id LIMIT ?",
            args.toArray());
    boolean more = rows.size() > size;
    var selected = rows.subList(0, Math.min(size, rows.size()));
    String next = null;
    if (more) {
      var last = selected.getLast();
      int type =
          "DISH".equals(last.get("target_type"))
              ? 0
              : "SETMEAL".equals(last.get("target_type")) ? 1 : 2;
      next =
          pages.encode(
              range,
              new DiscoveryCursor.Position(
                  Db.id(last, "store_id"), type, Db.id(last, "target_id")));
    }
    return new CursorPages.Page<>(
        selected.stream()
            .map(
                r ->
                    new Favorite(
                        s(r, "target_type"),
                        s(r, "target_id"),
                        s(r, "store_id"),
                        s(r, "store_name"),
                        s(r, "name")))
            .toList(),
        next,
        more);
  }
}
