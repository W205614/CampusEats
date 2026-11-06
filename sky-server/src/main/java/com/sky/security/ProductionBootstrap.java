package com.sky.security;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Db;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("prod")
@Order(20)
public class ProductionBootstrap implements ApplicationRunner {
  private final Db db;
  private final AuthService auth;
  private final Clock clock;
  private final String password;

  public ProductionBootstrap(
      Db db,
      AuthService auth,
      Clock clock,
      @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
    this.db = db;
    this.auth = auth;
    this.clock = clock;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // Both nodes lock the same persistent row, including when employee is initially empty.
    var bootstrap =
        db.one("SELECT initialized_at FROM production_bootstrap_lock WHERE id=1 FOR UPDATE");
    long employees = db.count("SELECT COUNT(*) FROM employee");
    if (bootstrap.get("initialized_at") != null) {
      require(employees > 0, 503, "PRODUCTION_ADMIN_MISSING", "生产管理员记录缺失，请先核实数据库恢复状态");
      return;
    }
    require(
        employees == 0, 503, "PRODUCTION_DATABASE_NOT_EMPTY", "首次生产初始化必须使用没有员工账号的新库，禁止自动沿用演示或试用身份");
    require(
        password.length() >= 12 && password.length() <= 128 && !password.contains("replace-"),
        503,
        "PRODUCTION_BOOTSTRAP_PASSWORD",
        "首次生产部署需提供12至128字符独立管理员初始密码");
    var now = LocalDateTime.now(clock);
    db.update(
        "INSERT INTO"
            + " employee(name,username,password,role,status,phone,sex,id_number,must_change_password,create_time,update_time)"
            + " VALUES('平台管理员','admin',?,'PLATFORM_ADMIN',1,'','','',true,?,?)",
        auth.encodePassword(password),
        now,
        now);
    db.update("UPDATE production_bootstrap_lock SET initialized_at=UTC_TIMESTAMP(6) WHERE id=1");
  }
}
