package com.sky.security;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.api.Commands;
import com.sky.common.BusinessException;
import com.sky.infra.Db;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=local-user-test-secret-thirty-two-characters",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false"
    })
@Testcontainers
class LocalUserIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("local_user_test")
          .withUsername("test")
          .withPassword("test")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired LocalUserService users;
  @Autowired AuthService auth;
  @Autowired Db db;
  private final Actor platform = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false);

  @BeforeEach
  void platform() {
    act(platform);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private void act(Actor actor) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  private String name() {
    return "local_" + UUID.randomUUID().toString().substring(0, 12);
  }

  private String token(Map<String, Object> value) {
    return value.get("token").toString();
  }

  @Test
  void accountLifecycleRevokesOldSessionsAndKeepsUserOwnership() {
    String username = name();
    var created =
        users.create(
            new LocalUserService.Create(
                username.toUpperCase(Locale.ROOT), "独立测试用户", "Initial-Test!2026"));
    assertEquals(username, created.username());
    assertTrue(created.enabled());
    assertTrue(created.mustChangePassword());
    String first = token(users.login(new LocalUserService.Login(username, "Initial-Test!2026")));
    Actor firstActor = auth.authenticate(first);
    assertEquals(created.id(), firstActor.id());
    assertTrue(firstActor.mustChangePassword());
    act(firstActor);
    users.change(new Commands.Password("Initial-Test!2026", "Changed-Test!2026"));
    assertEquals(
        401, assertThrows(BusinessException.class, () -> auth.authenticate(first)).status());
    String changed = token(users.login(new LocalUserService.Login(username, "Changed-Test!2026")));
    assertFalse(auth.authenticate(changed).mustChangePassword());
    assertTrue(
        db.one("SELECT password_hash FROM local_user_account WHERE user_id=?", created.id())
            .get("password_hash")
            .toString()
            .startsWith("$argon2"));
    act(platform);
    users.reset(created.id(), new LocalUserService.Reset("Reset-Test!2026"));
    assertEquals(
        401, assertThrows(BusinessException.class, () -> auth.authenticate(changed)).status());
    assertTrue(
        users
            .login(new LocalUserService.Login(username, "Reset-Test!2026"))
            .get("mustChangePassword")
            .equals(true));
    users.update(created.id(), new LocalUserService.Update("停用测试用户", false));
    var disabled =
        assertThrows(
            BusinessException.class,
            () -> users.login(new LocalUserService.Login(username, "Reset-Test!2026")));
    var missing =
        assertThrows(
            BusinessException.class,
            () -> users.login(new LocalUserService.Login(name(), "Reset-Test!2026")));
    assertEquals("BAD_CREDENTIALS", disabled.code());
    assertEquals(disabled.code(), missing.code());
  }

  @Test
  void completePaginationBindsFiltersAndIdentityWithoutReturningSecrets() {
    String prefix = name();
    String hash = auth.encodePassword("Fixture-Test!2026");
    for (int i = 0; i < 205; i++) {
      long user =
          db.insert("INSERT INTO user(name,create_time) VALUES('分页测试用户',CURRENT_TIMESTAMP)");
      db.update(
          "INSERT INTO local_user_account(user_id,username,password_hash,created_at)"
              + " VALUES(?,?,?,CURRENT_TIMESTAMP)",
          user,
          prefix + "_" + i,
          hash);
    }
    var ids = new HashSet<Long>();
    String cursor = null;
    int pages = 0;
    do {
      var page = users.page(prefix, true, cursor, 50);
      ++pages;
      for (var row : page.records()) assertTrue(ids.add(row.id()));
      cursor = page.nextCursor();
    } while (cursor != null);
    assertEquals(205, ids.size());
    assertEquals(5, pages);
    var first = users.page(prefix, true, null, 50);
    assertEquals(
        400,
        assertThrows(
                BusinessException.class, () -> users.page(prefix, false, first.nextCursor(), 50))
            .status());
    act(new Actor(2, "ADMIN", "PLATFORM_ADMIN", "", false));
    assertEquals(
        400,
        assertThrows(
                BusinessException.class, () -> users.page(prefix, true, first.nextCursor(), 50))
            .status());
    act(new Actor(100, "ADMIN", "SHOP_MANAGER", "", false, 1L, 1L));
    assertEquals(
        403,
        assertThrows(BusinessException.class, () -> users.page(prefix, true, null, 50)).status());
  }
}
