package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import com.sky.security.Actor;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PlatformScope {
  private final Db db;
  private final FulfillmentStages stages;
  private final BusinessMessages messages;

  public PlatformScope(Db db) {
    this(db, null, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public PlatformScope(Db db, FulfillmentStages stages, BusinessMessages messages) {
    this.db = db;
    this.stages = stages;
    this.messages = messages;
  }

  public Actor platform() {
    Actor a = Actor.employee();
    require(a.role().equals("PLATFORM_ADMIN"), 403, "FORBIDDEN", "需要平台管理员权限");
    return a;
  }

  public Map<String, Object> store(long id, boolean trading) {
    var row =
        db.one(
            "SELECT s.*,c.enabled campus_enabled,c.name campus_name FROM store s JOIN campus c ON"
                + " c.id=s.campus_id WHERE s.id=?"
                + (trading ? " FOR SHARE" : ""),
            id);
    require(row != null, 404, "NOT_FOUND", "店铺不存在");
    if (trading)
      require(
          Db.bool(row, "enabled") && Db.bool(row, "campus_enabled"),
          409,
          "STORE_DISABLED",
          "校区或店铺已停用");
    return row;
  }

  public Actor merchant(long id, boolean manage) {
    Actor a = Actor.employee();
    store(id, false);
    require(
        a.role().equals("PLATFORM_ADMIN")
            || (Objects.equals(a.storeId(), id)
                && (a.role().equals("SHOP_MANAGER") || (!manage && a.role().equals("OPERATOR")))),
        403,
        "FORBIDDEN",
        "无权操作该店铺");
    return a;
  }

  public Actor courier(long campus) {
    Actor a = Actor.employee();
    require(
        a.role().equals("DELIVERER") && Objects.equals(a.campusId(), campus),
        403,
        "FORBIDDEN",
        "仅本校区配送员可执行");
    return a;
  }

  public Map<String, Object> order(long id, boolean lock) {
    var row = db.one("SELECT * FROM orders WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    require(row != null, 404, "NOT_FOUND", "订单不存在");
    Actor a = Actor.current();
    if (a.type().equals("USER"))
      require(Db.id(row, "user_id") == a.id(), 404, "NOT_FOUND", "订单不存在");
    else if (a.role().equals("DELIVERER"))
      require(
          row.get("courier_id") != null
              && Db.id(row, "courier_id") == a.id()
              && Objects.equals(a.campusId(), Db.id(row, "campus_id")),
          404,
          "NOT_FOUND",
          "订单不存在");
    else merchant(Db.id(row, "store_id"), false);
    return row;
  }

  public void audit(String action, long target, Long store, String detail) {
    Actor a = Actor.current();
    db.update(
        "INSERT INTO"
            + " audit_log(actor_type,actor_id,action,target_id,detail,created_at,store_id,campus_id)"
            + " VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,?,?)",
        a.type(),
        a.id(),
        action,
        target,
        detail,
        store,
        store == null ? null : Db.id(store(store, false), "campus_id"));
  }

  public void event(long order, String type) {
    var ownership = db.one("SELECT store_id,campus_id FROM orders WHERE id=?", order);
    long event =
        db.insert(
            "INSERT INTO"
                + " outbox_event(event_type,aggregate_id,payload,next_attempt_at,created_at,store_id,campus_id)"
                + " VALUES(?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,?)",
            type,
            order,
            "{\"orderId\":\"" + order + "\",\"type\":\"" + type + "\"}",
            ownership.get("store_id"),
            ownership.get("campus_id"));
    if (stages != null) stages.sync(order);
    if (messages != null) messages.publish(event, order, type);
  }

  public static String table(String type) {
    require("DISH".equals(type) || "SETMEAL".equals(type), 400, "INVALID_ITEM", "商品类型不正确");
    return type.equals("DISH") ? "dish" : "setmeal";
  }
}
