package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.*;
import com.sky.business.MenuCache;
import com.sky.infra.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreCatalog {
  private final Db db;
  private final PlatformScope scope;
  private final MenuCache cache;
  private final Json json;
  private final Clock clock;

  public StoreCatalog(Db db, PlatformScope scope, MenuCache cache, Json json, Clock clock) {
    this.db = db;
    this.scope = scope;
    this.cache = cache;
    this.json = json;
    this.clock = clock;
  }

  public List<Map<String, Object>> categories(long store, boolean admin) {
    if (admin) scope.merchant(store, true);
    else scope.store(store, true);
    return db.rows(
        "SELECT * FROM category WHERE store_id=?"
            + (admin ? "" : " AND status=1")
            + " ORDER BY sort,id",
        store);
  }

  @Transactional
  public long category(long store, Long id, Category input) {
    scope.merchant(store, true);
    long target;
    if (id == null)
      target =
          db.insert(
              "INSERT INTO category(store_id,name,type,sort,status) VALUES(?,?,?,?,?)",
              store,
              input.name(),
              input.type(),
              input.sort(),
              input.enabled());
    else {
      var old = db.one("SELECT * FROM category WHERE id=? AND store_id=? FOR UPDATE", id, store);
      require(old != null, 404, "NOT_FOUND", "分类不存在");
      require(
          Db.integer(old, "type") == input.type()
              || db.count(
                      "SELECT (SELECT COUNT(*) FROM dish WHERE category_id=?)+(SELECT COUNT(*) FROM"
                          + " setmeal WHERE category_id=?)",
                      id,
                      id)
                  == 0,
          409,
          "CATEGORY_IN_USE",
          "分类已有商品，不能改变类型");
      db.update(
          "UPDATE category SET name=?,type=?,sort=?,status=? WHERE id=?",
          input.name(),
          input.type(),
          input.sort(),
          input.enabled(),
          id);
      target = id;
    }
    cache.afterCommit(key(store));
    scope.audit("CATEGORY_SAVE", target, store, "更新店内分类");
    return target;
  }

  public String key(long store) {
    return "store:" + store + ":catalog";
  }

  public Map<String, Object> product(long store, String type, long id, boolean lock) {
    var p =
        db.one(
            "SELECT p.*,c.status category_status FROM "
                + PlatformScope.table(type)
                + " p JOIN category c ON c.id=p.category_id AND c.store_id=p.store_id WHERE p.id=?"
                + " AND p.store_id=?"
                + (lock ? " FOR SHARE" : ""),
            id,
            store);
    require(p != null, 404, "NOT_FOUND", "本店商品不存在");
    return p;
  }

  @Transactional
  public long save(long store, String type, Long id, Product input) {
    scope.merchant(store, true);
    String table = PlatformScope.table(type);
    var category =
        db.one(
            "SELECT * FROM category WHERE id=? AND store_id=? FOR SHARE",
            input.categoryId(),
            store);
    require(
        category != null && Db.integer(category, "type") == (type.equals("DISH") ? 1 : 2),
        400,
        "INVALID_CATEGORY",
        "请选择本店相应类型分类");
    if (id != null) product(store, type, id, true);
    require(
        input.image() == null
            || input.image().isBlank()
            || input.image().matches("/uploads/[a-f0-9-]+\\.png")
            || input.image().equals("/demo-images/dish.svg"),
        400,
        "INVALID_IMAGE",
        "请使用本地商品图片");
    var components = input.components() == null ? List.<Component>of() : input.components();
    var flavors = input.flavors() == null ? List.<Flavor>of() : input.flavors();
    if (type.equals("SETMEAL")) {
      require(
          !components.isEmpty() && components.size() <= 50,
          400,
          "INVALID_COMPONENTS",
          "套餐须包含1至50种菜品");
      Set<Long> seen = new HashSet<>();
      for (var c : components.stream().sorted(Comparator.comparing(Component::dishId)).toList()) {
        require(seen.add(c.dishId()), 400, "INVALID_COMPONENTS", "套餐菜品重复");
        var dish = product(store, "DISH", c.dishId(), true);
        require(
            !input.enabled()
                || (Db.integer(dish, "status") == 1 && Db.integer(dish, "category_status") == 1),
            409,
            "ITEM_UNAVAILABLE",
            "套餐包含停售菜品");
      }
    } else {
      Set<String> names = new HashSet<>();
      for (var f : flavors)
        require(
            names.add(f.name()) && json.write(f.values()).length() <= 255,
            400,
            "INVALID_FLAVOR",
            "口味重复或内容过长");
    }
    long target =
        id == null
            ? db.insert(
                "INSERT INTO "
                    + table
                    + "(store_id,name,category_id,price,status,description,image)"
                    + " VALUES(?,?,?,?,?,?,?)",
                store,
                input.name(),
                input.categoryId(),
                input.price(),
                input.enabled(),
                input.description(),
                input.image())
            : id;
    if (id != null)
      db.update(
          "UPDATE "
              + table
              + " SET name=?,category_id=?,price=?,status=?,description=?,image=? WHERE id=? AND"
              + " store_id=?",
          input.name(),
          input.categoryId(),
          input.price(),
          input.enabled(),
          input.description(),
          input.image(),
          id,
          store);
    if (type.equals("DISH")) {
      db.update("DELETE FROM dish_flavor WHERE dish_id=?", target);
      for (var f : flavors)
        db.update(
            "INSERT INTO dish_flavor(dish_id,name,value) VALUES(?,?,?)",
            target,
            f.name(),
            json.write(f.values()));
      if (!input.enabled())
        db.update(
            "UPDATE setmeal s JOIN setmeal_dish sd ON sd.setmeal_id=s.id SET s.status=0 WHERE"
                + " s.store_id=? AND sd.dish_id=?",
            store,
            target);
    } else {
      db.update("DELETE FROM setmeal_dish WHERE setmeal_id=?", target);
      for (var c : components)
        db.update(
            "INSERT INTO setmeal_dish(setmeal_id,dish_id,copies,name,price) SELECT"
                + " ?,id,?,name,price FROM dish WHERE id=? AND store_id=?",
            target,
            c.copies(),
            c.dishId(),
            store);
    }
    cache.afterCommit(key(store));
    scope.audit("PRODUCT_SAVE", target, store, "更新店内商品");
    return target;
  }

  public List<Map<String, Object>> products(
      long store, boolean admin, String query, Long category) {
    if (admin) scope.merchant(store, true);
    else scope.store(store, true);
    var rows = admin ? load(store) : cache.load(key(store), () -> load(store));
    var out = new ArrayList<Map<String, Object>>();
    for (var source : rows) {
      var row = normalized(source);
      if (!admin
          && (Integer.parseInt(row.get("status").toString()) != 1
              || Integer.parseInt(row.get("category_status").toString()) != 1)) continue;
      if (query != null && !row.get("name").toString().contains(query.strip())) continue;
      if (category != null && Long.parseLong(row.get("category_id").toString()) != category)
        continue;
      String type = row.get("item_type").toString();
      long id = Long.parseLong(row.get("id").toString());
      row.put("remaining", remaining(type, id));
      var promo =
          db.one(
              "SELECT id,price_cents,ends_at FROM promotion WHERE store_id=? AND item_type=? AND"
                  + " item_id=? AND enabled=true AND starts_at<=? AND ends_at>?",
              store,
              type,
              id,
              now(),
              now());
      row.put("promotion", promo);
      out.add(row);
    }
    return out;
  }

  private List<Map<String, Object>> load(long store) {
    var out = new ArrayList<Map<String, Object>>();
    for (String type : List.of("DISH", "SETMEAL")) {
      var rows =
          db.rows(
              "SELECT p.*,c.status category_status FROM "
                  + PlatformScope.table(type)
                  + " p JOIN category c ON c.id=p.category_id AND c.store_id=p.store_id WHERE"
                  + " p.store_id=? ORDER BY p.id",
              store);
      for (var row : rows) {
        row.put("item_type", type);
        if (type.equals("DISH")) {
          var fs =
              db.rows(
                  "SELECT id,name,value FROM dish_flavor WHERE dish_id=? ORDER BY id",
                  row.get("id"));
          for (var f : fs) f.put("values", json.strings(f.remove("value").toString()));
          row.put("flavors", fs);
        } else
          row.put(
              "components",
              db.rows(
                  "SELECT sd.*,d.name current_name,d.status FROM setmeal_dish sd JOIN dish d ON"
                      + " d.id=sd.dish_id WHERE sd.setmeal_id=? ORDER BY sd.dish_id",
                  row.get("id")));
        out.add(row);
      }
    }
    return out;
  }

  public int remaining(String type, long id) {
    if (type.equals("DISH")) {
      var r =
          db.one(
              "SELECT"
                  + " COALESCE(q.total,d.default_quota)-COALESCE(q.reserved,0)-COALESCE(q.consumed,0)"
                  + " remaining FROM dish d LEFT JOIN daily_quota q ON q.dish_id=d.id AND"
                  + " q.business_date=? WHERE d.id=?",
              LocalDate.now(clock),
              id);
      return r == null ? 0 : Db.integer(r, "remaining");
    }
    var cs = db.rows("SELECT dish_id,copies FROM setmeal_dish WHERE setmeal_id=?", id);
    int left = cs.isEmpty() ? 0 : Integer.MAX_VALUE;
    for (var c : cs)
      left = Math.min(left, remaining("DISH", Db.id(c, "dish_id")) / Db.integer(c, "copies"));
    return left;
  }

  public List<Map<String, Object>> quotas(long store, LocalDate date) {
    scope.merchant(store, true);
    return db.rows(
        "SELECT d.id dish_id,d.name,d.default_quota,COALESCE(q.total,d.default_quota)"
            + " total,COALESCE(q.reserved,0) reserved,COALESCE(q.consumed,0) consumed FROM dish d"
            + " LEFT JOIN daily_quota q ON q.dish_id=d.id AND q.business_date=? WHERE d.store_id=?"
            + " ORDER BY d.id",
        date,
        store);
  }

  @Transactional
  public void quota(long store, LocalDate date, Quota input) {
    scope.merchant(store, true);
    product(store, "DISH", input.dishId(), true);
    if (input.defaultQuota())
      db.update(
          "UPDATE dish SET default_quota=? WHERE id=? AND store_id=?",
          input.total(),
          input.dishId(),
          store);
    else {
      db.update(
          "INSERT INTO daily_quota(business_date,dish_id,total) VALUES(?,?,?) ON DUPLICATE KEY"
              + " UPDATE dish_id=VALUES(dish_id)",
          date,
          input.dishId(),
          input.total());
      require(
          db.update(
                  "UPDATE daily_quota SET total=? WHERE business_date=? AND dish_id=? AND"
                      + " reserved+consumed<=?",
                  input.total(),
                  date,
                  input.dishId(),
                  input.total())
              == 1,
          409,
          "QUOTA_TOO_LOW",
          "配额不能低于已占用份数");
    }
    scope.audit("QUOTA_SAVE", input.dishId(), store, "更新店内配额");
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> normalized(Map<String, Object> source) {
    var result = new LinkedHashMap<String, Object>();
    for (var entry : source.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Number n && (key.equals("id") || key.endsWith("_id")))
        value = n.longValue();
      else if (value instanceof Number && key.equals("price"))
        value = Money.decimal(Money.cents(value));
      else if (value instanceof List<?> list)
        value =
            list.stream()
                .map(v -> v instanceof Map<?, ?> m ? normalized((Map<String, Object>) m) : v)
                .toList();
      result.put(key, value);
    }
    return result;
  }
}
