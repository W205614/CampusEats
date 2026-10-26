package com.sky.platform;

import static com.sky.common.BusinessException.require;

import com.sky.infra.*;
import com.sky.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DirectoryService {
  private final Db db;
  private final PlatformScope scope;
  private final AuthService auth;
  private final Json json;
  private final Clock clock;

  public DirectoryService(Db db, PlatformScope scope, AuthService auth, Json json, Clock clock) {
    this.db = db;
    this.scope = scope;
    this.auth = auth;
    this.json = json;
    this.clock = clock;
  }

  public List<Map<String, Object>> campuses(boolean admin) {
    if (admin) scope.platform();
    return db.rows("SELECT * FROM campus" + (admin ? "" : " WHERE enabled=true") + " ORDER BY id");
  }

  @Transactional
  public long campus(Long id, PlatformCommands.Campus input) {
    scope.platform();
    long target =
        id == null
            ? db.insert(
                "INSERT INTO campus(name,enabled) VALUES(?,?)", input.name(), input.enabled())
            : id;
    if (id != null)
      require(
          db.update(
                  "UPDATE campus SET name=?,enabled=? WHERE id=?",
                  input.name(),
                  input.enabled(),
                  id)
              == 1,
          404,
          "NOT_FOUND",
          "校区不存在");
    scope.audit("CAMPUS_SAVE", target, null, "更新校区");
    return target;
  }

  public List<Map<String, Object>> stores(Long campus, String query, boolean admin) {
    Actor a = admin ? Actor.employee() : null;
    var args = new ArrayList<Object>();
    String sql =
        "SELECT s.*,c.name campus_name FROM store s JOIN campus c ON c.id=s.campus_id WHERE 1=1";
    if (!admin) sql += " AND s.enabled=true AND c.enabled=true";
    else if (!a.role().equals("PLATFORM_ADMIN")) {
      require(a.storeId() != null, 403, "FORBIDDEN", "无店铺管理权限");
      sql += " AND s.id=?";
      args.add(a.storeId());
    }
    if (campus != null) {
      sql += " AND s.campus_id=?";
      args.add(campus);
    }
    if (query != null && !query.isBlank()) {
      sql += " AND s.name LIKE ?";
      args.add("%" + query.strip() + "%");
    }
    var rows = db.rows(sql + " ORDER BY s.id LIMIT 200", args.toArray());
    for (var row : rows) {
      row.put("accepting", accepting(row));
      row.put("hours", json.strings(row.get("hours").toString()));
    }
    return rows;
  }

  public boolean accepting(Map<String, Object> s) {
    return accepting(s, LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Shanghai"))));
  }

  public boolean accepting(Map<String, Object> s, LocalDateTime at) {
    if (!Db.bool(s, "open") || !Db.bool(s, "enabled")) return false;
    int now = at.getHour() * 60 + at.getMinute();
    for (String w : json.strings(s.get("hours").toString())) {
      String[] x = w.split("-");
      if (now >= minutes(x[0]) && now < minutes(x[1])) return true;
    }
    return false;
  }

  private int minutes(String t) {
    return t.equals("24:00")
        ? 1440
        : LocalTime.parse(t).getHour() * 60 + LocalTime.parse(t).getMinute();
  }

  @Transactional
  public long store(Long id, PlatformCommands.Store input) {
    if (id == null) scope.platform();
    else scope.merchant(id, true);
    boolean platform = Actor.current().role().equals("PLATFORM_ADMIN");
    var campus = db.one("SELECT * FROM campus WHERE id=? FOR SHARE", input.campusId());
    require(campus != null, 400, "INVALID_CAMPUS", "校区不存在");
    for (String w : input.hours()) {
      var x = w.split("-");
      require(minutes(x[0]) < minutes(x[1]), 400, "INVALID_HOURS", "营业时段开始须早于结束");
    }
    if (id != null) {
      var old = db.one("SELECT * FROM store WHERE id=? FOR UPDATE", id);
      require(old != null, 404, "NOT_FOUND", "店铺不存在");
      require(Db.id(old, "campus_id") == input.campusId(), 409, "IMMUTABLE_CAMPUS", "店铺创建后不能迁移校区");
      if (!platform)
        require(Db.bool(old, "enabled") == input.enabled(), 403, "FORBIDDEN", "店铺启停由平台管理");
    }
    long target =
        id == null
            ? db.insert(
                "INSERT INTO"
                    + " store(campus_id,name,enabled,open,delivery_fee,packaging_fee,hours,phone)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                input.campusId(),
                input.name(),
                input.enabled(),
                input.open(),
                Money.decimal(input.deliveryFeeCents()),
                Money.decimal(input.packagingFeeCents()),
                json.write(input.hours()),
                input.phone() == null ? "" : input.phone())
            : id;
    if (id != null && platform)
      db.update(
          "UPDATE store SET"
              + " name=?,enabled=?,open=?,delivery_fee=?,packaging_fee=?,hours=?,phone=?,version=version+1"
              + " WHERE id=?",
          input.name(),
          input.enabled(),
          input.open(),
          Money.decimal(input.deliveryFeeCents()),
          Money.decimal(input.packagingFeeCents()),
          json.write(input.hours()),
          input.phone() == null ? "" : input.phone(),
          id);
    else if (id != null)
      // A merchant never writes platform-owned availability, even from a stale settings form.
      db.update(
          "UPDATE store SET"
              + " name=?,open=?,delivery_fee=?,packaging_fee=?,hours=?,phone=?,version=version+1"
              + " WHERE id=?",
          input.name(),
          input.open(),
          Money.decimal(input.deliveryFeeCents()),
          Money.decimal(input.packagingFeeCents()),
          json.write(input.hours()),
          input.phone() == null ? "" : input.phone(),
          id);
    scope.audit("STORE_SAVE", target, target, "更新店铺及营业规则");
    return target;
  }

  public List<Map<String, Object>> buildings(long campus, boolean admin) {
    if (admin) scope.platform();
    return db.rows(
        "SELECT b.* FROM campus_building b JOIN campus c ON c.id=b.campus_id WHERE b.campus_id=?"
            + (admin ? "" : " AND b.enabled=true AND c.enabled=true")
            + " ORDER BY b.id",
        campus);
  }

  @Transactional
  public long building(long campus, Long id, com.sky.api.Commands.Building input) {
    scope.platform();
    require(
        db.count("SELECT COUNT(*) FROM campus WHERE id=?", campus) == 1, 404, "NOT_FOUND", "校区不存在");
    long target =
        id == null
            ? db.insert(
                "INSERT INTO campus_building(campus_id,name,enabled) VALUES(?,?,?)",
                campus,
                input.name(),
                input.enabled())
            : id;
    if (id != null)
      require(
          db.update(
                  "UPDATE campus_building SET name=?,enabled=? WHERE id=? AND campus_id=?",
                  input.name(),
                  input.enabled(),
                  id,
                  campus)
              == 1,
          404,
          "NOT_FOUND",
          "楼栋不存在");
    scope.audit("BUILDING_SAVE", target, null, "更新楼栋");
    return target;
  }

  public List<Map<String, Object>> couriers(long store) {
    scope.merchant(store, false);
    return db.rows(
        "SELECT id,name FROM employee WHERE role='DELIVERER' AND status=1 AND campus_id=? ORDER BY id",
        scope.store(store, false).get("campus_id"));
  }

  public List<Map<String, Object>> staff(Long store) {
    if (store == null) scope.platform();
    else scope.merchant(store, true);
    return db.rows(
        "SELECT id,username,name,role,store_id,campus_id,status,must_change_password FROM employee"
            + (store == null ? "" : " WHERE store_id=?")
            + " ORDER BY id",
        store == null ? new Object[] {} : new Object[] {store});
  }

  @Transactional
  public long staff(Long id, PlatformCommands.Staff input) {
    Actor a = Actor.employee();
    boolean platform = a.role().equals("PLATFORM_ADMIN");
    // Serialize administrator changes before locking the edited employee. Two admins
    // must not each disable the other using sessions authenticated before either commit.
    List<Map<String, Object>> administrators =
        platform
            ? db.rows(
                "SELECT id,status FROM employee WHERE role='PLATFORM_ADMIN' ORDER BY id FOR UPDATE")
            : List.of();
    if (id != null)
      require(
          db.one("SELECT id FROM employee WHERE id=? FOR UPDATE", id) != null,
          404,
          "NOT_FOUND",
          "员工不存在");
    if (id != null && (!input.enabled() || !input.role().equals("PLATFORM_ADMIN"))) {
      boolean removesAdministrator =
          administrators.stream()
              .anyMatch(e -> Db.id(e, "id") == id && Db.integer(e, "status") == 1);
      require(
          !removesAdministrator
              || administrators.stream().filter(e -> Db.integer(e, "status") == 1).count() > 1,
          409,
          "LAST_PLATFORM_ADMIN",
          "至少保留一名启用的平台管理员");
    }
    if (!platform) {
      require(a.storeId() != null, 403, "FORBIDDEN", "无员工管理权限");
      scope.merchant(a.storeId(), true);
      require(
          "OPERATOR".equals(input.role()) && Objects.equals(a.storeId(), input.storeId()),
          403,
          "FORBIDDEN",
          "店长只能管理本店运营员");
      if (id != null)
        require(
            db.count(
                    "SELECT COUNT(*) FROM employee WHERE id=? AND store_id=? AND role='OPERATOR'",
                    id,
                    a.storeId())
                == 1,
            403,
            "FORBIDDEN",
            "无权修改该员工");
    }
    Long campus = input.campusId();
    Long store = input.storeId();
    if (Set.of("SHOP_MANAGER", "OPERATOR").contains(input.role())) {
      require(store != null, 400, "STORE_REQUIRED", "请选择店铺");
      campus = Db.id(scope.store(store, false), "campus_id");
    } else if (input.role().equals("DELIVERER")) {
      require(
          store == null
              && campus != null
              && db.count("SELECT COUNT(*) FROM campus WHERE id=?", campus) == 1,
          400,
          "CAMPUS_REQUIRED",
          "配送员须归属校区");
    } else {
      require(platform, 403, "FORBIDDEN", "需要平台权限");
      store = null;
      campus = null;
    }
    if (id != null && id == a.id())
      require(
          input.enabled() && input.role().equals(a.role()), 409, "SELF_LOCKOUT", "不能禁用自己或移除自己的角色");
    if (id == null) require(input.password() != null, 400, "PASSWORD_REQUIRED", "新员工需要初始密码");
    long target =
        id == null
            ? db.insert(
                "INSERT INTO"
                    + " employee(name,username,password,role,store_id,campus_id,status,phone,sex,id_number,must_change_password,create_time,update_time)"
                    + " VALUES(?,?,?,?,?,?,?,'','','',true,?,?)",
                input.name(),
                input.username(),
                auth.encodePassword(input.password()),
                input.role(),
                store,
                campus,
                input.enabled() ? 1 : 0,
                LocalDateTime.now(clock),
                LocalDateTime.now(clock))
            : id;
    if (id != null) {
      require(
          db.update(
                  "UPDATE employee SET"
                      + " name=?,username=?,role=?,store_id=?,campus_id=?,status=?,auth_version=auth_version+1,update_time=?"
                      + " WHERE id=?",
                  input.name(),
                  input.username(),
                  input.role(),
                  store,
                  campus,
                  input.enabled() ? 1 : 0,
                  LocalDateTime.now(clock),
                  id)
              == 1,
          404,
          "NOT_FOUND",
          "员工不存在");
      if (input.password() != null)
        db.update(
            "UPDATE employee SET password=?,must_change_password=true WHERE id=?",
            auth.encodePassword(input.password()),
            id);
      db.update(
          "UPDATE auth_session SET revoked=true WHERE subject_type='ADMIN' AND subject_id=?", id);
    }
    scope.audit("STAFF_SAVE", target, store, "更新人员归属及权限");
    return target;
  }
}
