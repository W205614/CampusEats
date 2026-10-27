package com.sky.security;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.api.Commands;
import com.sky.common.BusinessException;
import com.sky.infra.Db;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** Employee password rotation boundaries, backed only by disposable test databases. */
@SpringBootTest(properties = {
    "spring.profiles.active=demo",
    "campus.jwt-secret=staff-password-test-secret-thirty-two-characters",
    "campus.demo-admin-password=TestAdmin!2026",
    "campus.tasks-enabled=false"
})
@Testcontainers
class StaffPasswordIntegrationTest {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
      .withDatabaseName("staff_password_test").withUsername("test").withPassword("test")
      .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));
  @Container static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired AuthService auth;
  @Autowired Db db;

  private static final String INITIAL = "Initial-Staff!2026";
  private static final String CHANGED = "Changed-Staff!2026";

  private String employee() {
    String username = "pwd_" + UUID.randomUUID().toString().substring(0, 12);
    db.insert("INSERT INTO employee(name,username,password,role,status,must_change_password,phone,sex,id_number)"
        + " VALUES('密码边界人员',?,?,'PLATFORM_ADMIN',1,true,'','','')",
        username, auth.encodePassword(INITIAL));
    return username;
  }

  private String login(String username, String password) {
    return auth.login(new Commands.Login(username, password)).get("token").toString();
  }

  @Test
  void unchangedInitialPasswordKeepsFirstChangeRequirementAndBothSessionsValid() {
    String username = employee(), first = login(username, INITIAL), second = login(username, INITIAL);
    Actor actor = auth.authenticate(first);
    var before = db.one("SELECT password,auth_version FROM employee WHERE id=?", actor.id());

    var rejected = assertThrows(BusinessException.class,
        () -> auth.change(actor, new Commands.Password(INITIAL, INITIAL)));

    assertEquals(400, rejected.status());
    assertEquals("PASSWORD_UNCHANGED", rejected.code());
    var after = db.one("SELECT password,auth_version,must_change_password FROM employee WHERE id=?", actor.id());
    assertEquals(before.get("password"), after.get("password"));
    assertEquals(before.get("auth_version"), after.get("auth_version"));
    assertTrue(Db.bool(after, "must_change_password"));
    assertTrue(auth.authenticate(first).mustChangePassword());
    assertTrue(auth.authenticate(second).mustChangePassword());
    assertEquals(0, db.count("SELECT COUNT(*) FROM auth_session WHERE subject_type='ADMIN' AND subject_id=? AND revoked=true", actor.id()));
  }

  @Test
  void differentPasswordClearsFirstChangeAndRevokesEveryPreviousSession() {
    String username = employee(), first = login(username, INITIAL), second = login(username, INITIAL);
    Actor actor = auth.authenticate(first);
    long version = db.count("SELECT auth_version FROM employee WHERE id=?", actor.id());

    auth.change(actor, new Commands.Password(INITIAL, CHANGED));

    assertEquals(401, assertThrows(BusinessException.class, () -> auth.authenticate(first)).status());
    assertEquals(401, assertThrows(BusinessException.class, () -> auth.authenticate(second)).status());
    assertEquals(2, db.count("SELECT COUNT(*) FROM auth_session WHERE subject_type='ADMIN' AND subject_id=? AND revoked=true", actor.id()));
    assertEquals(version + 1, db.count("SELECT auth_version FROM employee WHERE id=?", actor.id()));
    var rejected = assertThrows(BusinessException.class, () -> login(username, INITIAL));
    assertEquals("BAD_CREDENTIALS", rejected.code());
    Actor renewed = auth.authenticate(login(username, CHANGED));
    assertEquals(actor.id(), renewed.id());
    assertFalse(renewed.mustChangePassword());
  }
}
