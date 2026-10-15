package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.business.CartService;
import com.sky.common.Hashes;
import com.sky.infra.*;
import com.sky.security.Actor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreCart {
  private final Db db;
  private final Json json;
  private final StoreCatalog catalog;
  private final CartService validation;
  private final PlatformScope scope;

  public StoreCart(
      Db db, Json json, StoreCatalog catalog, CartService validation, PlatformScope scope) {
    this.db = db;
    this.json = json;
    this.catalog = catalog;
    this.validation = validation;
    this.scope = scope;
  }

  public long lock(long user, long store) {
    db.update(
        "INSERT INTO store_cart_state(user_id,store_id) VALUES(?,?) ON DUPLICATE KEY UPDATE"
            + " user_id=VALUES(user_id)",
        user,
        store);
    return Db.id(
        db.one(
            "SELECT version FROM store_cart_state WHERE user_id=? AND store_id=? FOR UPDATE",
            user,
            store),
        "version");
  }

  public List<Map<String, Object>> items(long user, long store) {
    var rows =
        db.rows(
            "SELECT * FROM store_cart_item WHERE user_id=? AND store_id=? ORDER BY"
                + " item_type,item_id,flavor_hash",
            user,
            store);
    for (var row : rows) {
      var p = catalog.product(store, row.get("item_type").toString(), Db.id(row, "item_id"), true);
      row.put("name", p.get("name"));
      row.put("price", p.get("price"));
      row.put("image", p.get("image"));
      row.put("status", p.get("status"));
      row.put("flavors", json.flavors(row.get("flavors").toString()));
    }
    return rows;
  }

  @Transactional
  public Map<String, Object> view(long store) {
    long user = Actor.user().id();
    scope.store(store, false);
    long version = lock(user, store);
    return Map.of("storeId", store, "cartVersion", version, "items", items(user, store));
  }

  @Transactional
  public List<Map<String, Object>> all() {
    long user = Actor.user().id();
    var rows =
        db.rows(
            "SELECT c.store_id,s.name store_name,s.campus_id,p.name campus_name,c.version"
                + " cart_version FROM store_cart_state c JOIN store s ON s.id=c.store_id JOIN"
                + " campus p ON p.id=s.campus_id WHERE c.user_id=? ORDER BY s.campus_id,s.id",
            user);
    for (var row : rows) row.put("items", items(user, Db.id(row, "store_id")));
    return rows;
  }

  @Transactional
  public Map<String, Object> change(long store, PlatformCommands.CartChange input) {
    long user = Actor.user().id();
    scope.store(store, input.delta() > 0);
    long version = lock(user, store);
    require(version == input.cartVersion(), 409, "CART_CHANGED", "购物车已变化");
    require(input.delta() != 0, 400, "INVALID_QUANTITY", "数量变化不能为零");
    catalog.product(store, input.itemType(), input.itemId(), true);
    String canonical =
        input.delta() > 0
            ? validation.validate(input.itemType(), input.itemId(), input.flavors())
            : json.write(new TreeMap<>(input.flavors() == null ? Map.of() : input.flavors()));
    String hash = Hashes.sha256(canonical);
    var line =
        db.one(
            "SELECT * FROM store_cart_item WHERE user_id=? AND store_id=? AND item_type=? AND"
                + " item_id=? AND flavor_hash=?",
            user,
            store,
            input.itemType(),
            input.itemId(),
            hash);
    int next = (line == null ? 0 : Db.integer(line, "quantity")) + input.delta();
    require(next >= 0 && next <= 50, 400, "INVALID_QUANTITY", "每项数量须在0至50之间");
    if (next == 0 && line != null)
      db.update("DELETE FROM store_cart_item WHERE id=?", line.get("id"));
    else if (line == null) {
      require(
          next > 0
              && db.count(
                      "SELECT COUNT(*) FROM store_cart_item WHERE user_id=? AND store_id=?",
                      user,
                      store)
                  < 50,
          400,
          "CART_LIMIT",
          "单店最多50项");
      db.update(
          "INSERT INTO"
              + " store_cart_item(user_id,store_id,item_type,item_id,flavors,flavor_hash,quantity)"
              + " VALUES(?,?,?,?,?,?,?)",
          user,
          store,
          input.itemType(),
          input.itemId(),
          canonical,
          hash,
          next);
    } else db.update("UPDATE store_cart_item SET quantity=? WHERE id=?", next, line.get("id"));
    db.update(
        "UPDATE store_cart_state SET version=version+1 WHERE user_id=? AND store_id=?",
        user,
        store);
    return view(store);
  }

  public void clear(long user, long store) {
    db.update("DELETE FROM store_cart_item WHERE user_id=? AND store_id=?", user, store);
    db.update(
        "UPDATE store_cart_state SET version=version+1 WHERE user_id=? AND store_id=?",
        user,
        store);
  }

  @Transactional
  public void clearCurrent(long store, long expected) {
    long user = Actor.user().id();
    scope.store(store, false);
    require(lock(user, store) == expected, 409, "CART_CHANGED", "购物车已变化");
    clear(user, store);
  }

  @Transactional
  public Map<String, Object> reorder(long order, long expected) {
    long user = Actor.user().id();
    var original = scope.order(order, false);
    long store = Db.id(original, "store_id");
    long version = lock(user, store);
    require(version == expected, 409, "CART_CHANGED", "购物车已变化");
    var skipped = new ArrayList<String>();
    for (var detail : db.rows("SELECT * FROM order_detail WHERE order_id=? ORDER BY id", order)) {
      String type = detail.get("dish_id") == null ? "SETMEAL" : "DISH";
      long item = Db.id(detail, type.equals("DISH") ? "dish_id" : "setmeal_id");
      try {
        var selection =
            detail.get("dish_flavor") == null
                ? Map.<String, String>of()
                : json.flavors(detail.get("dish_flavor").toString());
        change(
            store,
            new PlatformCommands.CartChange(
                version, type, item, selection, Db.integer(detail, "number")));
        version++;
      } catch (com.sky.common.BusinessException ex) {
        skipped.add(detail.get("name") + ":" + ex.getMessage());
      } catch (tools.jackson.core.JacksonException ex) {
        skipped.add(detail.get("name") + ":旧口味需重新选择");
      }
    }
    return Map.of("cart", view(store), "skipped", skipped);
  }
}
