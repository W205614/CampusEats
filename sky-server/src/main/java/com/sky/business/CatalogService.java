package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.*;
import com.sky.infra.*;
import com.sky.mapper.CatalogReadMapper;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final Db db;
  private final Json json;
  private final Audit audit;
  private final CatalogReadMapper reader;
  private final MenuCache cache;
  private final Clock clock;

  public CatalogService(
      Db db, Json json, Audit audit, CatalogReadMapper reader, MenuCache cache, Clock clock) {
    this.db = db;
    this.json = json;
    this.audit = audit;
    this.reader = reader;
    this.cache = cache;
    this.clock = clock;
  }

  public List<Map<String, Object>> categories(boolean admin) {
    if (admin) Actor.admin();
    return db.rows(
        "SELECT * FROM category" + (admin ? "" : " WHERE status=1") + " ORDER BY sort,id");
  }

  public List<Map<String, Object>> menu(long category, String type) {
    require(type.equals("DISH") || type.equals("SETMEAL"), 400, "INVALID_ITEM", "商品类型不正确");
    require(
        db.count(
                "SELECT COUNT(*) FROM category WHERE id=? AND status=1 AND type=?",
                category,
                type.equals("DISH") ? 1 : 2)
            == 1,
        404,
        "NOT_FOUND",
        "分类不存在");
    var items =
        cache.load(
            "menu:" + type + ":" + category,
            () -> {
              var rows = type.equals("DISH") ? reader.dishes(category) : reader.setmeals(category);
              if (!rows.isEmpty()) {
                var ids = rows.stream().map(x -> Db.id(x, "id")).toList();
                if (type.equals("DISH")) {
                  var flavors = reader.flavors(ids);
                  for (var row : rows) {
                    var own =
                        flavors.stream()
                            .filter(x -> Db.id(x, "dish_id") == Db.id(row, "id"))
                            .toList();
                    for (var flavor : own)
                      flavor.put("values", json.strings(String.valueOf(flavor.remove("value"))));
                    row.put("flavors", own);
                  }
                } else {
                  var components = reader.components(ids);
                  for (var row : rows)
                    row.put(
                        "components",
                        components.stream()
                            .filter(x -> Db.id(x, "setmeal_id") == Db.id(row, "id"))
                            .toList());
                }
              }
              return rows;
            });
    var quotaRows =
        db.rows(
            "SELECT"
                + " d.id,COALESCE(q.total,d.default_quota)-COALESCE(q.reserved,0)-COALESCE(q.consumed,0)"
                + " remaining FROM dish d LEFT JOIN daily_quota q ON q.dish_id=d.id AND"
                + " q.business_date=?",
            LocalDate.now(clock));
    Map<Long, Integer> quota = new HashMap<>();
    for (var q : quotaRows) quota.put(Db.id(q, "id"), Db.integer(q, "remaining"));
    for (var row : items) {
      long id = ((Number) row.get("id")).longValue();
      int remaining;
      if (type.equals("DISH")) remaining = quota.getOrDefault(id, 0);
      else {
        remaining = Integer.MAX_VALUE;
        @SuppressWarnings("unchecked")
        var cs = (List<Map<String, Object>>) row.get("components");
        if (cs.isEmpty()) remaining = 0;
        for (var c : cs)
          remaining =
              Math.min(
                  remaining,
                  quota.getOrDefault(((Number) c.get("dish_id")).longValue(), 0)
                      / ((Number) c.get("copies")).intValue());
      }
      row.put("remaining", remaining);
      row.put("item_type", type);
    }
    return items;
  }

  public List<Map<String, Object>> products(String type) {
    Actor.admin();
    var rows = db.rows("SELECT * FROM " + table(type) + " ORDER BY id DESC");
    if (!rows.isEmpty()) {
      var ids = rows.stream().map(x -> Db.id(x, "id")).toList();
      if (type.equals("DISH")) {
        var f = reader.flavors(ids);
        for (var row : rows) {
          var own = f.stream().filter(x -> Db.id(x, "dish_id") == Db.id(row, "id")).toList();
          for (var flavor : own)
            flavor.put("values", json.strings(String.valueOf(flavor.remove("value"))));
          row.put("flavors", own);
        }
      } else {
        var c = reader.components(ids);
        for (var row : rows)
          row.put(
              "components",
              c.stream().filter(x -> Db.id(x, "setmeal_id") == Db.id(row, "id")).toList());
      }
    }
    return rows;
  }

  @Transactional
  public long category(Long id, Category input) {
    Actor.admin();
    long target =
        id == null
            ? db.insert(
                "INSERT INTO"
                    + " category(name,type,sort,status,create_time,update_time,create_user,update_user)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                input.name(),
                input.type(),
                input.sort(),
                input.enabled() ? 1 : 0,
                LocalDateTime.now(clock),
                LocalDateTime.now(clock),
                Actor.current().id(),
                Actor.current().id())
            : id;
    if (id != null) {
      var current = db.one("SELECT * FROM category WHERE id=? FOR UPDATE", id);
      require(current != null, 404, "NOT_FOUND", "分类不存在");
      require(
          Db.integer(current, "type") == input.type()
              || db.count(
                      "SELECT (SELECT COUNT(*) FROM dish WHERE category_id=?)+(SELECT COUNT(*) FROM"
                          + " setmeal WHERE category_id=?)",
                      id,
                      id)
                  == 0,
          409,
          "CATEGORY_IN_USE",
          "已有商品的分类不能改变类型");
      db.update(
          "UPDATE category SET name=?,type=?,sort=?,status=?,update_time=? WHERE id=?",
          input.name(),
          input.type(),
          input.sort(),
          input.enabled() ? 1 : 0,
          LocalDateTime.now(clock),
          id);
    }
    Set<String> invalidations = new TreeSet<>(List.of("menu:DISH:" + target, "menu:SETMEAL:" + target));
    if (input.type() == 1)
      for (var dependent : db.rows(
          "SELECT DISTINCT s.category_id FROM setmeal s JOIN setmeal_dish sd ON sd.setmeal_id=s.id"
              + " JOIN dish d ON d.id=sd.dish_id WHERE d.category_id=?", target))
        invalidations.add("menu:SETMEAL:" + dependent.get("category_id"));
    invalidations.forEach(cache::afterCommit);
    audit.write(Actor.current(), "CATEGORY_SAVE", target, "更新分类");
    return target;
  }

  @Transactional
  public void deleteCategory(long id) {
    Actor.admin();
    // Serialize with product creation's FOR SHARE before checking references.
    require(db.one("SELECT id FROM category WHERE id=? FOR UPDATE", id) != null,
        404, "NOT_FOUND", "分类不存在");
    require(
        db.count(
                "SELECT (SELECT COUNT(*) FROM dish WHERE category_id=?)+(SELECT COUNT(*) FROM"
                    + " setmeal WHERE category_id=?)",
                id,
                id)
            == 0,
        409,
        "CATEGORY_IN_USE",
        "分类中仍有商品");
    require(db.update("DELETE FROM category WHERE id=?", id) == 1, 404, "NOT_FOUND", "分类不存在");
    audit.write(Actor.current(), "CATEGORY_DELETE", id, "删除空分类");
  }

  @Transactional
  public long save(String type, Long id, Product input) {
    Actor.admin();
    String table = table(type);
    var category = db.one("SELECT * FROM category WHERE id=? FOR SHARE", input.categoryId());
    require(
        category != null && Db.integer(category, "type") == (type.equals("DISH") ? 1 : 2),
        400,
        "INVALID_CATEGORY",
        "商品分类类型不正确");
    require(
        input.image() == null
            || input.image().isBlank()
            || input.image().startsWith("/uploads/")
            || input.image().equals("/demo-images/dish.svg"),
        400,
        "INVALID_IMAGE",
        "请使用本地上传图片");
    List<Component> components = input.components() == null ? List.of() : input.components();
    List<Flavor> flavors = input.flavors() == null ? List.of() : input.flavors();
    if (type.equals("SETMEAL")) {
      require(
          !components.isEmpty() && components.size() <= 50, 400, "INVALID_COMPONENTS", "套餐必须包含菜品");
      Set<Long> seen = new HashSet<>();
      for (var c : components.stream().sorted(Comparator.comparing(Component::dishId)).toList()) {
        require(seen.add(c.dishId()), 400, "DUPLICATE_COMPONENT", "套餐菜品不能重复");
        var dish =
            db.one(
                "SELECT d.*,c.status category_status FROM dish d JOIN category c ON"
                    + " c.id=d.category_id WHERE d.id=? FOR SHARE",
                c.dishId());
        require(
            dish != null
                && (!input.enabled()
                    || (Db.integer(dish, "status") == 1
                        && Db.integer(dish, "category_status") == 1)),
            409,
            "ITEM_UNAVAILABLE",
            "起售套餐必须使用起售菜品");
      }
    } else {
      Set<String> seen = new HashSet<>();
      for (var flavor : flavors)
        require(
            seen.add(flavor.name()) && json.write(flavor.values()).length() <= 255,
            400,
            "INVALID_FLAVOR",
            "口味重复或选项过长");
    }
    Map<String, Object> old =
        id == null ? null : db.one("SELECT * FROM " + table + " WHERE id=? FOR UPDATE", id);
    require(id == null || old != null, 404, "NOT_FOUND", "商品不存在");
    long target =
        id == null
            ? db.insert(
                "INSERT INTO "
                    + table
                    + "(name,category_id,price,status,description,image,create_time,update_time,create_user,update_user)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                input.name(),
                input.categoryId(),
                input.price(),
                input.enabled() ? 1 : 0,
                input.description(),
                input.image(),
                LocalDateTime.now(clock),
                LocalDateTime.now(clock),
                Actor.current().id(),
                Actor.current().id())
            : id;
    if (id != null)
      db.update(
          "UPDATE "
              + table
              + " SET name=?,category_id=?,price=?,status=?,description=?,image=?,update_time=?"
              + " WHERE id=?",
          input.name(),
          input.categoryId(),
          input.price(),
          input.enabled() ? 1 : 0,
          input.description(),
          input.image(),
          LocalDateTime.now(clock),
          id);
    Set<String> invalidations = new TreeSet<>();
    invalidations.add("menu:" + type + ":" + input.categoryId());
    if (old != null) invalidations.add("menu:" + type + ":" + old.get("category_id"));
    if (type.equals("DISH")) {
      db.update("DELETE FROM dish_flavor WHERE dish_id=?", target);
      for (var f : flavors)
        db.update(
            "INSERT INTO dish_flavor(dish_id,name,value) VALUES(?,?,?)",
            target,
            f.name(),
            json.write(f.values()));
      if (!input.enabled()) {
        db.update(
            "UPDATE setmeal s JOIN setmeal_dish sd ON sd.setmeal_id=s.id SET s.status=0 WHERE"
                + " sd.dish_id=?",
            target);
      }
      // Composition metadata and availability depend on every dish edit, not only disabling it.
      for (var dependent : db.rows(
          "SELECT DISTINCT s.category_id FROM setmeal s JOIN setmeal_dish sd ON sd.setmeal_id=s.id"
              + " WHERE sd.dish_id=?", target))
        invalidations.add("menu:SETMEAL:" + dependent.get("category_id"));
    } else {
      db.update("DELETE FROM setmeal_dish WHERE setmeal_id=?", target);
      for (var c : components)
        db.update(
            "INSERT INTO setmeal_dish(setmeal_id,dish_id,copies,name,price) SELECT"
                + " ?,id,?,name,price FROM dish WHERE id=?",
            target,
            c.copies(),
            c.dishId());
    }
    invalidations.forEach(cache::afterCommit);
    audit.write(Actor.current(), "PRODUCT_SAVE", target, type + " 更新商品");
    return target;
  }

  @Transactional
  public void delete(String type, long id) {
    Actor.admin();
    String table = table(type);
    var row = db.one("SELECT * FROM " + table + " WHERE id=? FOR UPDATE", id);
    require(row != null, 404, "NOT_FOUND", "商品不存在");
    require(Db.integer(row, "status") == 0, 409, "ITEM_ACTIVE", "停售后才能删除");
    if (type.equals("DISH"))
      require(
          db.count("SELECT COUNT(*) FROM setmeal_dish WHERE dish_id=?", id) == 0,
          409,
          "ITEM_IN_SETMEAL",
          "菜品仍被套餐引用");
    db.update(
        "DELETE FROM "
            + (type.equals("DISH") ? "dish_flavor WHERE dish_id" : "setmeal_dish WHERE setmeal_id")
            + "=?",
        id);
    db.update("DELETE FROM " + table + " WHERE id=?", id);
    cache.afterCommit("menu:" + type + ":" + row.get("category_id"));
    audit.write(Actor.current(), "PRODUCT_DELETE", id, type + " 删除商品");
  }

  private String table(String type) {
    require(type.equals("DISH") || type.equals("SETMEAL"), 400, "INVALID_ITEM", "商品类型不正确");
    return type.equals("DISH") ? "dish" : "setmeal";
  }
}
