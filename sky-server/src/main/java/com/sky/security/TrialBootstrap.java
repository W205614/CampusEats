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

/** Fresh trial stores receive one administrator, without importing shared demo identities. */
@Component
@Profile("trial")
@Order(10)
public class TrialBootstrap implements ApplicationRunner {
  private final Db db;
  private final AuthService auth;
  private final Clock clock;
  private final String password;

  public TrialBootstrap(
      Db db,
      AuthService auth,
      Clock clock,
      @Value("${campus.demo-admin-password}") String password) {
    this.db = db;
    this.auth = auth;
    this.clock = clock;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (db.count("SELECT COUNT(*) FROM employee") != 0) return;
    require(
        password.length() >= 12 && !password.contains("replace-with-"),
        503,
        "TRIAL_PASSWORD",
        "首次试用部署需要独立的安全初始化密码");
    db.update(
        "INSERT INTO"
            + " employee(name,username,password,role,status,phone,sex,id_number,must_change_password,create_time,update_time)"
            + " VALUES('平台管理员','admin',?,'PLATFORM_ADMIN',1,'','','',true,?,?)",
        auth.encodePassword(password),
        LocalDateTime.now(clock),
        LocalDateTime.now(clock));
  }
}
