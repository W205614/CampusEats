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
    var stock = new HashMap<Long, Integer>();
    for (var d :
        db.rows(
            "SELECT"
                + " d.id,COALESCE(q.total,d.default_quota)-COALESCE(q.reserved,0)-COALESCE(q.consumed,0)"
                + " remaining FROM dish d LEFT JOIN daily_quota q ON q.dish_id=d.id AND"
                + " q.business_date=? WHERE d.store_id=?",
            LocalDate.now(clock),
            store)) stock.put(Db.id(d, "id"), Db.integer(d, "remaining"));
    var components =
        grouped(
            db.rows(
                "SELECT sd.* FROM setmeal_dish sd JOIN setmeal s ON s.id=sd.setmeal_id WHERE"
                    + " s.store_id=? ORDER BY sd.dish_id",
                store),
            "setmeal_id");
    var promotions = new HashMap<String, Map<String, Object>>();
    var at = now();
    for (var promo :
        db.rows(
            "SELECT id,item_type,item_id,price_cents,ends_at FROM promotion WHERE store_id=? AND"
                + " enabled=true AND starts_at<=? AND ends_at>? ORDER BY id",
            store,
            at,
            at)) {
      String k = promo.remove("item_type") + ":" + promo.remove("item_id");
      promotions.putIfAbsent(k, promo);
    }
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
      int left = stock.getOrDefault(id, 0);
      if (type.equals("SETMEAL")) {
        var parts = components.getOrDefault(id, List.of());
        left = parts.isEmpty() ? 0 : Integer.MAX_VALUE;
        for (var part : parts)
          left =
              Math.min(
                  left, stock.getOrDefault(Db.id(part, "dish_id"), 0) / Db.integer(part, "copies"));
      }
      row.put("remaining", left);
      row.put("promotion", promotions.get(type + ":" + id));
      out.add(row);
    }
    return out;
  }

  /**
   * Bounded candidates first; all response details are fetched in batches, independent of store
   * count.
   */
  public List<Map<String, Object>> search(
      List<Long> stores, String query, Long category, LocalDateTime at) {
    if (stores.isEmpty()) return List.of();
    var params = new HashMap<String, Object>();
    params.put("stores", stores);
    params.put("query", query == null ? "" : query.strip());
    if (category != null) params.put("category", category);
    var branches = new ArrayList<String>();
    for (String type : List.of("DISH", "SETMEAL")) {
      branches.add(
          "SELECT p.id,p.store_id,'"
              + type
              + "' item_type,"
              + (type.equals("DISH") ? "0" : "1")
              + " item_rank,s.name store_name FROM "
              + PlatformScope.table(type)
              + " p JOIN category c ON c.id=p.category_id"
              + " AND c.store_id=p.store_id JOIN store s ON s.id=p.store_id"
              + " JOIN campus campus ON campus.id=s.campus_id WHERE p.store_id IN (:stores)"
              + " AND s.enabled=true AND campus.enabled=true AND p.status=1 AND c.status=1"
              + (category == null ? "" : " AND p.category_id=:category")
              // Binary substring matching preserves Java String.contains case and literal %/_
              // semantics.
              + " AND (LOCATE(CAST(:query AS BINARY),CAST(p.name AS BINARY))>0"
              + " OR LOCATE(CAST(:query AS BINARY),CAST(s.name AS BINARY))>0)");
    }
    var candidates =
        db.named.queryForList(
            String.join(" UNION ALL ", branches) + " ORDER BY store_id,item_rank,id LIMIT 200",
            params);
    if (candidates.isEmpty()) return List.of();
    var dishIds = new ArrayList<Long>();
    var setmealIds = new ArrayList<Long>();
    for (var row : candidates)
      (row.get("item_type").equals("DISH") ? dishIds : setmealIds).add(Db.id(row, "id"));
    var products = new HashMap<String, Map<String, Object>>();
    for (String type : List.of("DISH", "SETMEAL")) {
      var ids = type.equals("DISH") ? dishIds : setmealIds;
      if (ids.isEmpty()) continue;
      for (var row :
          db.named.queryForList(
              "SELECT p.*,c.status category_status FROM "
                  + PlatformScope.table(type)
                  + " p JOIN category c ON c.id=p.category_id"
                  + " AND c.store_id=p.store_id WHERE p.id IN (:ids)",
              Map.of("ids", ids))) products.put(type + ":" + Db.id(row, "id"), row);
    }
    var flavors =
        dishIds.isEmpty()
            ? Map.<Long, List<Map<String, Object>>>of()
            : grouped(
                db.named.queryForList(
                    "SELECT * FROM dish_flavor WHERE dish_id IN (:ids) ORDER BY id",
                    Map.of("ids", dishIds)),
                "dish_id");
    for (var rows : flavors.values())
      for (var row : rows) {
        row.remove("dish_id");
        row.put("values", json.strings(row.remove("value").toString()));
      }
    var components =
        setmealIds.isEmpty()
            ? Map.<Long, List<Map<String, Object>>>of()
            : grouped(
                db.named.queryForList(
                    "SELECT sd.*,d.name current_name,d.status FROM setmeal_dish sd JOIN dish d ON"
                        + " d.id=sd.dish_id WHERE sd.setmeal_id IN (:ids) ORDER BY sd.dish_id",
                    Map.of("ids", setmealIds)),
                "setmeal_id");
    var stockIds = new HashSet<>(dishIds);
    for (var rows : components.values()) for (var row : rows) stockIds.add(Db.id(row, "dish_id"));
    var stock = new HashMap<Long, Integer>();
    if (!stockIds.isEmpty())
      for (var row :
          db.named.queryForList(
              "SELECT d.id,COALESCE(q.total,d.default_quota)"
                  + "-COALESCE(q.reserved,0)-COALESCE(q.consumed,0) remaining FROM dish d"
                  + " LEFT JOIN daily_quota q ON q.dish_id=d.id AND q.business_date=:date"
                  + " WHERE d.id IN (:ids)",
              Map.of("date", at.toLocalDate(), "ids", stockIds)))
        stock.put(Db.id(row, "id"), Db.integer(row, "remaining"));
    var promotions = new HashMap<String, Map<String, Object>>();
    var promoParams = new HashMap<String, Object>();
    promoParams.put("stores", stores);
    promoParams.put("dishes", dishIds.isEmpty() ? List.of(0L) : dishIds);
    promoParams.put("setmeals", setmealIds.isEmpty() ? List.of(0L) : setmealIds);
    promoParams.put("at", at);
    for (var promo :
        db.named.queryForList(
            "SELECT id,item_type,item_id,price_cents,ends_at FROM promotion WHERE store_id IN"
                + " (:stores) AND enabled=true AND starts_at<=:at AND ends_at>:at AND"
                + " ((item_type='DISH' AND item_id IN (:dishes)) OR (item_type='SETMEAL' AND"
                + " item_id IN (:setmeals))) ORDER BY id",
            promoParams)) {
      String key = promo.remove("item_type") + ":" + promo.remove("item_id");
      promotions.putIfAbsent(key, promo);
    }
    var out = new ArrayList<Map<String, Object>>();
    for (var candidate : candidates) {
      String type = candidate.get("item_type").toString();
      long id = Db.id(candidate, "id");
      var source = products.get(type + ":" + id);
      if (source == null) continue; // A concurrently removed item is no longer a result.
      var row = normalized(source);
      row.put("item_type", type);
      row.put("store_name", candidate.get("store_name"));
      row.put(
          type.equals("DISH") ? "flavors" : "components",
          (type.equals("DISH") ? flavors : components).getOrDefault(id, List.of()));
      int left = stock.getOrDefault(id, 0);
      if (type.equals("SETMEAL")) {
        var parts = components.getOrDefault(id, List.of());
        left = parts.isEmpty() ? 0 : Integer.MAX_VALUE;
        for (var part : parts)
          left =
              Math.min(
                  left, stock.getOrDefault(Db.id(part, "dish_id"), 0) / Db.integer(part, "copies"));
      }
      row.put("remaining", left);
      row.put("promotion", promotions.get(type + ":" + id));
      out.add(row);
    }
    return out;
  }

  private List<Map<String, Object>> load(long store) {
    var flavors =
        grouped(
            db.rows(
                "SELECT f.* FROM dish_flavor f JOIN dish d ON d.id=f.dish_id WHERE d.store_id=?"
                    + " ORDER BY f.id",
                store),
            "dish_id");
    for (var fs : flavors.values())
      for (var f : fs) {
        f.remove("dish_id");
        f.put("values", json.strings(f.remove("value").toString()));
      }
    var components =
        grouped(
            db.rows(
                "SELECT sd.*,d.name current_name,d.status FROM setmeal_dish sd JOIN setmeal s ON"
                    + " s.id=sd.setmeal_id JOIN dish d ON d.id=sd.dish_id WHERE s.store_id=? ORDER"
                    + " BY sd.dish_id",
                store),
            "setmeal_id");
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
        row.put(
            type.equals("DISH") ? "flavors" : "components",
            (type.equals("DISH") ? flavors : components).getOrDefault(Db.id(row, "id"), List.of()));
        out.add(row);
      }
    }
    return out;
  }

  private Map<Long, List<Map<String, Object>>> grouped(List<Map<String, Object>> rows, String key) {
    var result = new HashMap<Long, List<Map<String, Object>>>();
    for (var row : rows)
      result.computeIfAbsent(Db.id(row, key), ignored -> new ArrayList<>()).add(row);
    return result;
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
