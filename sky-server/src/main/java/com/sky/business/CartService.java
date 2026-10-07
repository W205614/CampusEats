package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.*;
import com.sky.common.Hashes;
import com.sky.infra.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
  private final Db db;
  private final Json json;

  public CartService(Db db, Json json) {
    this.db = db;
    this.json = json;
  }

  public long lock(long user) {
    db.update(
        "INSERT INTO cart_state(user_id) VALUES(?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)",
        user);
    return Db.id(
        db.one("SELECT version FROM cart_state WHERE user_id=? FOR UPDATE", user), "version");
  }

  public void version(long actual, long expected) {
    require(actual == expected, 409, "CART_CHANGED", "购物车已变化，请刷新");
  }

  public List<Map<String, Object>> items(long user) {
    return items(user, false);
  }

  public List<Map<String, Object>> items(long user, boolean lock) {
    var rows =
        db.rows(
            "SELECT c.id,c.item_type,c.item_id,c.quantity,c.flavors,COALESCE(d.name,s.name)"
                + " name,COALESCE(d.price,s.price) price,COALESCE(d.image,s.image)"
                + " image,COALESCE(d.status,s.status) status FROM cart_item c LEFT JOIN dish d ON"
                + " c.item_type='DISH' AND d.id=c.item_id LEFT JOIN setmeal s ON"
                + " c.item_type='SETMEAL' AND s.id=c.item_id WHERE c.user_id=? ORDER BY c.id"
                + (lock ? " FOR SHARE" : ""),
            user);
    for (var row : rows) row.put("flavors", json.flavors(String.valueOf(row.get("flavors"))));
    return rows;
  }

  @Transactional(readOnly = true)
  public Cart view(long user) {
    long v = db.count("SELECT COALESCE(MAX(version),0) FROM cart_state WHERE user_id=?", user);
    return new Cart(v, ViewFactory.cart(items(user)));
  }

  public String validate(String type, long id, Map<String, String> selection) {
    require(type.equals("DISH") || type.equals("SETMEAL"), 400, "INVALID_ITEM", "商品类型错误");
    String table = type.equals("DISH") ? "dish" : "setmeal";
    var product =
        db.one(
            "SELECT p.id,p.status,c.status category_status FROM "
                + table
                + " p JOIN category c ON c.id=p.category_id WHERE p.id=? FOR SHARE",
            id);
    require(
        product != null
            && Db.integer(product, "status") == 1
            && Db.integer(product, "category_status") == 1,
        409,
        "ITEM_UNAVAILABLE",
        "商品已停售或删除");
    var chosen = new TreeMap<String, String>();
    if (selection != null) chosen.putAll(selection);
    if (type.equals("DISH")) {
      var definitions =
          db.rows("SELECT name,value FROM dish_flavor WHERE dish_id=? ORDER BY id", id);
      Set<String> names = new HashSet<>();
      for (var flavor : definitions) {
        String name = String.valueOf(flavor.get("name"));
        names.add(name);
        require(
            chosen.get(name) != null
                && json.strings(String.valueOf(flavor.get("value"))).contains(chosen.get(name)),
            409,
            "FLAVOR_CHANGED",
            "请选择当前有效的口味");
      }
      require(names.equals(chosen.keySet()), 400, "INVALID_FLAVOR", "口味选项不正确");
    } else {
      require(chosen.isEmpty(), 400, "INVALID_FLAVOR", "套餐不接受自定义口味");
      require(
          db.count("SELECT COUNT(*) FROM setmeal_dish WHERE setmeal_id=?", id) > 0
              && db.count(
                      "SELECT COUNT(*) FROM setmeal_dish sd LEFT JOIN dish d ON d.id=sd.dish_id"
                          + " LEFT JOIN category c ON c.id=d.category_id WHERE sd.setmeal_id=? AND"
                          + " (d.id IS NULL OR d.status<>1 OR c.status<>1 OR sd.copies IS NULL OR"
                          + " sd.copies<1)",
                      id)
                  == 0,
          409,
          "ITEM_UNAVAILABLE",
          "套餐组成菜品暂不可售");
    }
    String canonical = json.write(chosen);
    require(canonical.length() <= 1000, 400, "INVALID_FLAVOR", "口味内容过长");
    return canonical;
  }

  @Transactional
  public Cart change(long user, CartChange input) {
    long version = lock(user);
    version(version, input.cartVersion());
    require(input.delta() != 0, 400, "INVALID_QUANTITY", "数量变化不能为零");
    String canonical;
    if (input.delta() > 0) canonical = validate(input.itemType(), input.itemId(), input.flavors());
    else
      canonical = json.write(new TreeMap<>(input.flavors() == null ? Map.of() : input.flavors()));
    String hash = Hashes.sha256(canonical);
    var line =
        db.one(
            "SELECT * FROM cart_item WHERE user_id=? AND item_type=? AND item_id=? AND"
                + " flavor_hash=?",
            user,
            input.itemType(),
            input.itemId(),
            hash);
    int previous = line == null ? 0 : Db.integer(line, "quantity");
    int next = previous + input.delta();
    require(next >= 0 && next <= 50, 400, "INVALID_QUANTITY", "每项数量须在0到50之间");
    if (next == 0 && line != null) db.update("DELETE FROM cart_item WHERE id=?", line.get("id"));
    else if (line == null) {
      require(next > 0, 404, "ITEM_NOT_IN_CART", "购物车中没有该商品");
      require(
          db.count("SELECT COUNT(*) FROM cart_item WHERE user_id=?", user) < 50,
          400,
          "CART_LIMIT",
          "购物车最多50项");
      db.update(
          "INSERT INTO cart_item(user_id,item_type,item_id,flavors,flavor_hash,quantity)"
              + " VALUES(?,?,?,?,?,?)",
          user,
          input.itemType(),
          input.itemId(),
          canonical,
          hash,
          next);
    } else
      db.update(
          "UPDATE cart_item SET quantity=quantity+? WHERE id=?", input.delta(), line.get("id"));
    db.update("UPDATE cart_state SET version=version+1 WHERE user_id=?", user);
    return new Cart(version + 1, ViewFactory.cart(items(user)));
  }

  @Transactional
  public Cart clear(long user, long expected) {
    long v = lock(user);
    version(v, expected);
    clearLocked(user);
    return new Cart(v + 1, List.of());
  }

  public void clearLocked(long user) {
    db.update("DELETE FROM cart_item WHERE user_id=?", user);
    db.update("UPDATE cart_state SET version=version+1 WHERE user_id=?", user);
  }

  @Transactional
  public Map<String, Object> reorder(long user, long order, long expected) {
    long v = lock(user);
    version(v, expected);
    require(
        db.one("SELECT id FROM orders WHERE id=? AND user_id=?", order, user) != null,
        404,
        "NOT_FOUND",
        "订单不存在");
    List<String> skipped = new ArrayList<>();
    for (var row : db.rows("SELECT * FROM order_detail WHERE order_id=? ORDER BY id", order)) {
      String type = row.get("dish_id") == null ? "SETMEAL" : "DISH";
      long id = Db.id(row, type.equals("DISH") ? "dish_id" : "setmeal_id");
      Map<String, String> flavor;
      try {
        flavor =
            row.get("dish_flavor") == null
                ? Map.of()
                : json.flavors(String.valueOf(row.get("dish_flavor")));
      } catch (Exception e) {
        skipped.add(row.get("name") + ":旧口味需重新选择");
        continue;
      }
      try {
        change(user, new CartChange(v, type, id, flavor, Db.integer(row, "number")));
        v++;
      } catch (com.sky.common.BusinessException ex) {
        skipped.add(row.get("name") + ":" + ex.getMessage());
      }
    }
    return Map.of("cart", new Cart(v, ViewFactory.cart(items(user))), "skipped", skipped);
  }
}
