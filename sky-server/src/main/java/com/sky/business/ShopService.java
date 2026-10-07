package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.*;
import com.sky.infra.*;
import com.sky.security.Actor;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShopService {
  private final Db db;
  private final Json json;
  private final Clock clock;
  private final Audit audit;

  public ShopService(Db db, Json json, Clock clock, Audit audit) {
    this.db = db;
    this.json = json;
    this.clock = clock;
    this.audit = audit;
  }

  public Map<String, Object> settings(boolean lock) {
    return db.one("SELECT * FROM shop_settings WHERE id=1" + (lock ? " FOR SHARE" : ""));
  }

  public Map<String, Object> view() {
    var row = settings(false);
    row.put("hours", json.strings(String.valueOf(row.get("hours"))));
    row.put("accepting", accepting(row));
    return row;
  }

  public boolean accepting(Map<String, Object> row) {
    if (!Db.bool(row, "open")) return false;
    var raw = row.get("hours");
    List<String> windows =
        raw instanceof List<?>
            ? ((List<?>) raw).stream().map(Object::toString).toList()
            : json.strings(raw.toString());
    int now = LocalTime.now(clock).getHour() * 60 + LocalTime.now(clock).getMinute();
    for (String w : windows) {
      var x = w.split("-");
      if (now >= minutes(x[0]) && now < minutes(x[1])) return true;
    }
    return false;
  }

  private int minutes(String text) {
    if (text.equals("24:00")) return 1440;
    var t = LocalTime.parse(text);
    return t.getHour() * 60 + t.getMinute();
  }

  @Transactional
  public void configure(Shop input) {
    Actor.admin();
    for (String w : input.hours()) {
      var x = w.split("-");
      require(minutes(x[0]) < minutes(x[1]), 400, "INVALID_HOURS", "营业时段的开始必须早于结束");
    }
    db.update(
        "UPDATE shop_settings SET"
            + " open=?,delivery_fee=?,packaging_fee=?,hours=?,phone=?,version=version+1 WHERE id=1",
        input.open(),
        input.deliveryFee().setScale(2, RoundingMode.HALF_UP),
        input.packagingFee().setScale(2, RoundingMode.HALF_UP),
        json.write(input.hours()),
        input.phone() == null ? "" : input.phone());
    audit.write(Actor.current(), "SHOP_CONFIG", 1, "更新营业规则");
  }

  public List<Map<String, Object>> buildings(boolean admin) {
    return db.rows(
        "SELECT * FROM campus_building" + (admin ? "" : " WHERE enabled=true") + " ORDER BY id");
  }

  @Transactional
  public long building(Long id, Building input) {
    Actor.admin();
    long target =
        id == null
            ? db.insert(
                "INSERT INTO campus_building(name,enabled) VALUES(?,?)",
                input.name(),
                input.enabled())
            : id;
    if (id != null)
      require(
          db.update(
                  "UPDATE campus_building SET name=?,enabled=? WHERE id=?",
                  input.name(),
                  input.enabled(),
                  id)
              == 1,
          404,
          "NOT_FOUND",
          "楼栋不存在");
    audit.write(Actor.current(), "BUILDING_SAVE", target, "更新配送楼栋");
    return target;
  }
}
