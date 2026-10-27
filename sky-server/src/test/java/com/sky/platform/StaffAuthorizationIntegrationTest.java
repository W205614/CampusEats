package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sky.api.Commands;
import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** Personnel writes revalidate real sessions after their database lock wait. */
@SpringBootTest(properties = {
    "spring.profiles.active=demo",
    "campus.jwt-secret=staff-authorization-test-secret-thirty-two-bytes",
    "campus.demo-admin-password=TestAdmin!2026",
    "campus.tasks-enabled=false"
})
@Testcontainers
class StaffAuthorizationIntegrationTest {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
      .withDatabaseName("staff_authorization_test").withUsername("test").withPassword("test")
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

  @MockitoSpyBean Db db;
  @Autowired AuthService auth;
  @Autowired DirectoryService directory;
  @Autowired Clock clock;

  static final String ADMIN_LOCK =
      "SELECT id,status FROM employee WHERE role='PLATFORM_ADMIN' ORDER BY id FOR UPDATE";
  static final String INITIAL = "Guard-Initial!2026";
  static final String RESET = "Guard-Reset!2026";
  static final String ATTEMPTED = "Guard-StaleSave!2026";
  String prefix, encoded, staleToken;
  long campus, store, otherCampus, otherStore, first, second, shopManager, operator;
  Actor administrator, stale, manager;

  enum Change { DISABLE, DEMOTE, RESET }
  enum SessionChange { REVOKED, EXPIRED, WRONG_SUBJECT, WRONG_TYPE, VERSION }

  @BeforeEach
  void fixture() {
    reset(db);
    prefix = "guard_" + UUID.randomUUID().toString().substring(0, 8);
    if (encoded == null) encoded = auth.encodePassword(INITIAL);
    campus = db.insert("INSERT INTO campus(name) VALUES(?)", prefix);
    store = db.insert("INSERT INTO store(campus_id,name) VALUES(?,?)", campus, prefix);
    otherCampus = db.insert("INSERT INTO campus(name) VALUES(?)", prefix + "_other");
    otherStore = db.insert("INSERT INTO store(campus_id,name) VALUES(?,?)", otherCampus, prefix + "_other");
    first = employee("_a", "PLATFORM_ADMIN", null, null);
    second = employee("_b", "PLATFORM_ADMIN", null, null);
    shopManager = employee("_manager", "SHOP_MANAGER", store, campus);
    operator = employee("_operator", "OPERATOR", store, campus);
    administrator = session(first);
    staleToken = auth.issue("ADMIN", second).get("token").toString();
    stale = auth.authenticate(staleToken);
    manager = session(shopManager);
  }

  @AfterEach
  void clean() {
    reset(db);
    SecurityContextHolder.clearContext();
    var ids = db.rows("SELECT id FROM employee WHERE username LIKE ?", prefix + "%");
    for (var row : ids) {
      db.update("DELETE FROM auth_session WHERE subject_type='ADMIN' AND subject_id=?", row.get("id"));
      db.update("DELETE FROM employee WHERE id=?", row.get("id"));
    }
    db.update("DELETE FROM store WHERE id IN(?,?)", store, otherStore);
    db.update("DELETE FROM campus WHERE id IN(?,?)", campus, otherCampus);
  }

  private long employee(String suffix, String role, Long ownedStore, Long ownedCampus) {
    return db.insert("INSERT INTO employee(name,username,password,role,store_id,campus_id,status,"
            + "must_change_password,phone,sex,id_number) VALUES(?,?,?,?,?,?,1,false,'','','')",
        prefix + suffix, prefix + suffix, encoded, role, ownedStore, ownedCampus);
  }

  private Actor session(long employee) {
    return auth.authenticate(auth.issue("ADMIN", employee).get("token").toString());
  }

  private void act(Actor actor) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  private PlatformCommands.Staff input(long employee, String role, Long ownedStore,
      Long ownedCampus, boolean enabled, String password) {
    String username = db.one("SELECT username FROM employee WHERE id=?", employee).get("username").toString();
    return new PlatformCommands.Staff(username, "已保存的员工", role, ownedStore, ownedCampus, enabled, password);
  }

  private void changeSecond(Change change) {
    act(administrator);
    directory.staff(second, input(second, change == Change.DEMOTE ? "OPERATOR" : "PLATFORM_ADMIN",
        change == Change.DEMOTE ? store : null, change == Change.DEMOTE ? campus : null,
        change != Change.DISABLE, change == Change.RESET ? RESET : null));
  }

  /** Pause only the already-authenticated request, before its first personnel lock. */
  private BusinessException delayed(Actor actor, Runnable command, Runnable change) throws Exception {
    var beforeLock = new CountDownLatch(1);
    var continueRequest = new CountDownLatch(1);
    var worker = new AtomicReference<Thread>();
    var once = new AtomicBoolean();
    doAnswer(call -> {
      if (Thread.currentThread() == worker.get() && once.compareAndSet(false, true)) {
        beforeLock.countDown();
        assertTrue(continueRequest.await(10, TimeUnit.SECONDS));
      }
      return call.callRealMethod();
    }).when(db).rows(ADMIN_LOCK);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = executor.submit(() -> {
        worker.set(Thread.currentThread());
        act(actor);
        try { return assertThrows(BusinessException.class, command::run); }
        finally { SecurityContextHolder.clearContext(); }
      });
      try {
        assertTrue(beforeLock.await(10, TimeUnit.SECONDS));
        change.run();
      } finally { continueRequest.countDown(); }
      return pending.get(10, TimeUnit.SECONDS);
    } finally {
      continueRequest.countDown();
      reset(db);
    }
  }

  private void unauthorized(BusinessException failure) {
    assertEquals(401, failure.status());
    assertEquals("UNAUTHORIZED", failure.code());
  }

  @ParameterizedTest
  @EnumSource(Change.class)
  void authenticatedSelfSaveCannotReverseDisableDemotionOrPasswordReset(Change change) throws Exception {
    var selfSave = input(second, "PLATFORM_ADMIN", null, null, true,
        change == Change.RESET ? ATTEMPTED : null);
    var committed = new AtomicReference<Map<String, Object>>();

    unauthorized(delayed(stale, () -> directory.staff(second, selfSave), () -> {
      changeSecond(change);
      committed.set(db.one("SELECT * FROM employee WHERE id=?", second));
    }));

    assertEquals(committed.get(), db.one("SELECT * FROM employee WHERE id=?", second));
    assertEquals(0, db.count("SELECT COUNT(*) FROM audit_log WHERE actor_id=? AND action='STAFF_SAVE'", second));
    assertEquals(401, assertThrows(BusinessException.class, () -> auth.authenticate(staleToken)).status());
    var oldSession = db.one("SELECT revoked FROM auth_session WHERE jti=?", stale.jti());
    assertTrue(Db.bool(oldSession, "revoked"));
    if (change == Change.RESET) {
      String username = prefix + "_b";
      assertEquals("BAD_CREDENTIALS", assertThrows(BusinessException.class,
          () -> auth.login(new Commands.Login(username, ATTEMPTED))).code());
      var renewed = auth.authenticate(auth.login(new Commands.Login(username, RESET)).get("token").toString());
      assertEquals("PLATFORM_ADMIN", renewed.role());
      assertTrue(renewed.mustChangePassword());
    }
  }

  @Test
  void passwordResetAlsoStopsAnInFlightReplacementAdministratorCreation() throws Exception {
    var newAdministrator = new PlatformCommands.Staff(prefix + "_replacement", "拒绝的替代管理员",
        "PLATFORM_ADMIN", null, null, true, ATTEMPTED);
    unauthorized(delayed(stale, () -> directory.staff(null, newAdministrator), () -> changeSecond(Change.RESET)));
    assertEquals(0, db.count("SELECT COUNT(*) FROM employee WHERE username=?", prefix + "_replacement"));
    assertEquals(0, db.count("SELECT COUNT(*) FROM audit_log WHERE actor_id=? AND action='STAFF_SAVE'", second));
  }

  @Test
  void transferredManagerCannotUseThePreviousStoreScopeAfterAuthentication() throws Exception {
    var operatorEdit = input(operator, "OPERATOR", store, campus, true, null);
    unauthorized(delayed(manager, () -> directory.staff(operator, operatorEdit), () -> {
      act(administrator);
      directory.staff(shopManager, input(shopManager, "SHOP_MANAGER", otherStore, otherCampus, true, null));
    }));
    assertEquals(prefix + "_operator", db.one("SELECT name FROM employee WHERE id=?", operator).get("name"));
    assertEquals(otherStore, db.count("SELECT store_id FROM employee WHERE id=?", shopManager));
  }

  @ParameterizedTest
  @EnumSource(SessionChange.class)
  void invalidOriginalSessionCannotAuthorizeAnyPersonnelWrite(SessionChange change) {
    switch (change) {
      case REVOKED -> auth.logout(stale);
      case EXPIRED -> db.update("UPDATE auth_session SET expires_at=? WHERE jti=?",
          LocalDateTime.now(clock).withNano(0), stale.jti());
      case WRONG_SUBJECT -> db.update("UPDATE auth_session SET subject_id=? WHERE jti=?", first, stale.jti());
      case WRONG_TYPE -> db.update("UPDATE auth_session SET subject_type='USER' WHERE jti=?", stale.jti());
      case VERSION -> db.update("UPDATE employee SET auth_version=auth_version+1 WHERE id=?", second);
    }
    var before = db.one("SELECT * FROM employee WHERE id=?", operator);
    act(stale);
    unauthorized(assertThrows(BusinessException.class,
        () -> directory.staff(operator, input(operator, "OPERATOR", store, campus, true, null))));
    assertEquals(before, db.one("SELECT * FROM employee WHERE id=?", operator));
  }

  @Test
  void emptySyntheticJtiIsNeverAProductionAuthorizationBypass() {
    act(new Actor(first, "ADMIN", "PLATFORM_ADMIN", "", false));
    unauthorized(assertThrows(BusinessException.class,
        () -> directory.staff(operator, input(operator, "OPERATOR", store, campus, true, null))));
  }

  @Test
  void realAdministratorAndManagerSessionsRetainNormalPersonnelManagementBoundaries() {
    act(administrator);
    long created = directory.staff(null, new PlatformCommands.Staff(prefix + "_new_operator", "新运营员",
        "OPERATOR", store, campus, true, INITIAL));
    act(manager);
    directory.staff(created, input(created, "OPERATOR", store, campus, true, null));
    assertEquals("已保存的员工", db.one("SELECT name FROM employee WHERE id=?", created).get("name"));
    assertEquals("FORBIDDEN", assertThrows(BusinessException.class,
        () -> directory.staff(null, new PlatformCommands.Staff(prefix + "_forbidden", "拒绝的管理员",
            "PLATFORM_ADMIN", null, null, true, INITIAL))).code());
    assertEquals(0, db.count("SELECT COUNT(*) FROM employee WHERE username=?", prefix + "_forbidden"));
    act(session(operator));
    var denied = assertThrows(BusinessException.class,
        () -> directory.staff(created, input(created, "OPERATOR", store, campus, true, null)));
    assertEquals(403, denied.status());
    assertEquals("FORBIDDEN", denied.code());
  }

  @Test
  void lastEnabledAdministratorProtectionStillAppliesToACurrentSession() {
    var previous = db.rows("SELECT id,status FROM employee WHERE role='PLATFORM_ADMIN'");
    try {
      db.update("UPDATE employee SET status=0 WHERE role='PLATFORM_ADMIN' AND id<>?", first);
      act(administrator);
      var failure = assertThrows(BusinessException.class,
          () -> directory.staff(first, input(first, "PLATFORM_ADMIN", null, null, false, null)));
      assertEquals("LAST_PLATFORM_ADMIN", failure.code());
      assertEquals(1, db.count("SELECT COUNT(*) FROM employee WHERE role='PLATFORM_ADMIN' AND status=1"));
    } finally {
      for (var row : previous) db.update("UPDATE employee SET status=? WHERE id=?", row.get("status"), row.get("id"));
    }
  }

  @Test
  void managersWithInvalidCrossTargetsUseTheSameLockHierarchyWithoutADeadlock() throws Exception {
    long otherManager = employee("_second_manager", "SHOP_MANAGER", store, campus);
    Actor other = session(otherManager);
    var start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var one = workers.submit(() -> invalidManagerEdit(start, manager, otherManager));
      var two = workers.submit(() -> invalidManagerEdit(start, other, shopManager));
      start.countDown();
      assertEquals("FORBIDDEN", one.get(10, TimeUnit.SECONDS));
      assertEquals("FORBIDDEN", two.get(10, TimeUnit.SECONDS));
    }
  }

  private String invalidManagerEdit(CountDownLatch start, Actor actor, long target) throws Exception {
    start.await();
    act(actor);
    try {
      return assertThrows(BusinessException.class,
          () -> directory.staff(target, input(target, "OPERATOR", store, campus, true, null))).code();
    } finally { SecurityContextHolder.clearContext(); }
  }
}
