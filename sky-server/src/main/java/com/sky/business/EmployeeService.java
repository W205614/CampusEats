package com.sky.business;

import static com.sky.common.BusinessException.require;

import com.sky.api.Commands.Employee;
import com.sky.infra.Db;
import com.sky.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {
  private final Db db;
  private final AuthService auth;
  private final Audit audit;
  private final Clock clock;

  public EmployeeService(Db db, AuthService auth, Audit audit, Clock clock) {
    this.db = db;
    this.auth = auth;
    this.audit = audit;
    this.clock = clock;
  }

  public List<Map<String, Object>> list() {
    Actor.admin();
    return db.rows(
        "SELECT id,name,username,role,status,must_change_password FROM employee ORDER BY id");
  }

  public List<Map<String, Object>> couriers() {
    Actor.employee();
    return db.rows("SELECT id,name FROM employee WHERE role='DELIVERER' AND status=1 ORDER BY id");
  }

  @Transactional
  public long save(Long id, Employee input) {
    Actor.admin();
    if (id == null) require(input.password() != null, 400, "PASSWORD_REQUIRED", "新员工必须设置初始密码");
    if (id != null && id == Actor.current().id())
      require(
          input.enabled() && input.role().equals("ADMIN"),
          409,
          "SELF_LOCKOUT",
          "不能禁用自己或移除自己的管理员角色");
    long target;
    if (id == null)
      target =
          db.insert(
              "INSERT INTO"
                  + " employee(name,username,password,role,status,phone,sex,id_number,must_change_password,create_time,update_time)"
                  + " VALUES(?,?,?,?,?,'','','',true,?,?)",
              input.name(),
              input.username(),
              auth.encodePassword(input.password()),
              input.role(),
              input.enabled() ? 1 : 0,
              LocalDateTime.now(clock),
              LocalDateTime.now(clock));
    else {
      target = id;
      require(
          db.update(
                  "UPDATE employee SET"
                      + " name=?,username=?,role=?,status=?,auth_version=auth_version+1,update_time=?"
                      + " WHERE id=?",
                  input.name(),
                  input.username(),
                  input.role(),
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
    audit.write(Actor.current(), "EMPLOYEE_SAVE", target, "更新员工角色或状态");
    return target;
  }
}
