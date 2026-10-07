package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.Quota;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotaService {
  private final Db db;
  private final Audit audit;

  public QuotaService(Db db, Audit audit) {
    this.db = db;
    this.audit = audit;
  }

  public void reserve(long order, LocalDate date, Map<Long, Integer> quantities) {
    for (var entry : new TreeMap<>(quantities).entrySet()) {
      long dish = entry.getKey();
      int qty = entry.getValue();
      int changed = reserveExisting(date, dish, qty);
      if (changed == 0) {
        // Duplicate initialization takes an exclusive lock; INSERT IGNORE would create a shared
        // duplicate-key lock and deadlock when competing transactions upgrade it for the UPDATE.
        var product = db.one("SELECT default_quota FROM dish WHERE id=? FOR SHARE", dish);
        require(product != null, 409, "ITEM_UNAVAILABLE", "菜品已删除");
        db.update(
            "INSERT INTO daily_quota(business_date,dish_id,total) VALUES(?,?,?) ON DUPLICATE KEY"
                + " UPDATE dish_id=VALUES(dish_id)",
            date,
            dish,
            product.get("default_quota"));
        changed = reserveExisting(date, dish, qty);
      }
      require(changed == 1, 409, "SOLD_OUT", "剩余份数不足，请调整购物车");
      db.update(
          "INSERT INTO order_reservation(order_id,dish_id,business_date,quantity,state)"
              + " VALUES(?,?,?,?,'RESERVED')",
          order,
          dish,
          date,
          qty);
    }
  }

  private int reserveExisting(LocalDate date, long dish, int quantity) {
    return db.update(
        "UPDATE daily_quota SET reserved=reserved+? WHERE business_date=? AND dish_id=?"
            + " AND total-reserved-consumed>=?",
        quantity,
        date,
        dish,
        quantity);
  }

  public void transition(long order, boolean consume) {
    for (var row :
        db.rows(
            "SELECT * FROM order_reservation WHERE order_id=? ORDER BY dish_id FOR UPDATE",
            order)) {
      if (!"RESERVED".equals(row.get("state"))) continue;
      int quantity = Db.integer(row, "quantity");
      int changed =
          db.update(
              "UPDATE daily_quota SET reserved=reserved-?,consumed=consumed+? WHERE business_date=?"
                  + " AND dish_id=? AND reserved>=?",
              quantity,
              consume ? quantity : 0,
              row.get("business_date"),
              row.get("dish_id"),
              quantity);
      require(changed == 1, 409, "QUOTA_INCONSISTENT", "配额账目需要检查");
      db.update(
          "UPDATE order_reservation SET state=? WHERE order_id=? AND dish_id=? AND"
              + " state='RESERVED'",
          consume ? "CONSUMED" : "RELEASED",
          order,
          row.get("dish_id"));
    }
  }

  public List<Map<String, Object>> list(LocalDate date) {
    return db.rows(
        "SELECT d.id dish_id,d.name,d.default_quota,COALESCE(q.total,d.default_quota)"
            + " total,COALESCE(q.reserved,0) reserved,COALESCE(q.consumed,0)"
            + " consumed,COALESCE(q.total,d.default_quota)-COALESCE(q.reserved,0)-COALESCE(q.consumed,0)"
            + " remaining FROM dish d LEFT JOIN daily_quota q ON q.dish_id=d.id AND"
            + " q.business_date=? ORDER BY d.id",
        date);
  }

  @Transactional
  public void configure(LocalDate date, Quota input) {
    Actor.admin();
    require(
        db.one("SELECT id FROM dish WHERE id=? FOR UPDATE", input.dishId()) != null,
        404,
        "NOT_FOUND",
        "菜品不存在");
    if (input.defaultQuota())
      db.update("UPDATE dish SET default_quota=? WHERE id=?", input.total(), input.dishId());
    else {
      db.update(
          "INSERT IGNORE INTO daily_quota(business_date,dish_id,total) VALUES(?,?,?)",
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
          "总配额不能低于预占和已消耗数量");
    }
    audit.write(Actor.current(), "QUOTA_SAVE", input.dishId(), "更新配额");
  }
}
