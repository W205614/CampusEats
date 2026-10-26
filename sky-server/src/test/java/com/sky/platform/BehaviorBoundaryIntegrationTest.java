package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** Only the settings ownership race and appeal-after-fulfilment transitions. */
@SpringBootTest(properties = {
    "spring.profiles.active=demo",
    "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
    "campus.demo-admin-password=TestAdmin!2026",
    "campus.tasks-enabled=false"
})
@Testcontainers
class BehaviorBoundaryIntegrationTest {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
      .withDatabaseName("behavior_boundaries").withUsername("test").withPassword("test")
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
  @Autowired DirectoryService directory;
  @Autowired AftersaleService aftersales;
  @Autowired Clock clock;
  long campus, store, user, order, detail;
  Actor manager, customer;
  final Actor platform = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false);

  @BeforeEach
  void fixture() {
    act(platform);
    campus = db.insert("INSERT INTO campus(name) VALUES(?)", "boundary-" + UUID.randomUUID());
    store = db.insert("INSERT INTO store(campus_id,name,open) VALUES(?,'边界店铺',true)", campus);
    manager = new Actor(100, "ADMIN", "SHOP_MANAGER", "", false, store, campus);
    user = db.insert("INSERT INTO user(openid,create_time) VALUES(?,CURRENT_TIMESTAMP)", UUID.randomUUID().toString());
    customer = new Actor(user, "USER", "USER", "", false);
    order = db.insert("INSERT INTO orders(number,status,user_id,address_book_id,order_time,amount,"
        + "store_id,campus_id,paid_cents,pay_status,fulfillment) VALUES(?,3,?,0,CURRENT_TIMESTAMP,10,?,?,1000,1,'DELIVERY')",
        "BOUNDARY-" + UUID.randomUUID(), user, store, campus);
    detail = db.insert("INSERT INTO order_detail(order_id,name,number,amount,line_total_cents) VALUES(?,'测试餐品',1,10,1000)", order);
  }

  @AfterEach
  void clearActor() { SecurityContextHolder.clearContext(); }

  private void act(Actor actor) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  private PlatformCommands.Store settings(boolean enabled, String name) {
    return new PlatformCommands.Store(campus, name, enabled, true, 600, 100, List.of("00:00-24:00"), "");
  }

  @Test
  void platformDisableWinsWhenMerchantHasAlreadyReadOldAvailability() throws Exception {
    var platformLocked = new CountDownLatch(1);
    var releasePlatform = new CountDownLatch(1);
    var merchantWaiting = new CountDownLatch(1);
    doAnswer(call -> {
      if (Actor.current().role().equals("SHOP_MANAGER")) merchantWaiting.countDown();
      Object result = call.callRealMethod();
      if (Actor.current().role().equals("PLATFORM_ADMIN")) {
        platformLocked.countDown();
        assertTrue(releasePlatform.await(10, TimeUnit.SECONDS));
      }
      return result;
    }).when(db).one("SELECT * FROM store WHERE id=? FOR UPDATE", store);

    try (var workers = Executors.newFixedThreadPool(2)) {
      Future<?> disabling = workers.submit(() -> {
        act(platform);
        try { directory.store(store, settings(false, "平台停用")); }
        finally { clearActor(); }
      });
      assertTrue(platformLocked.await(10, TimeUnit.SECONDS));
      Future<BusinessException> saving = workers.submit(() -> {
        act(manager);
        try {
          return assertThrows(BusinessException.class, () -> directory.store(store, settings(true, "店长旧表单")));
        } finally { clearActor(); }
      });
      try {
        assertTrue(merchantWaiting.await(10, TimeUnit.SECONDS));
      } finally { releasePlatform.countDown(); }
      disabling.get(10, TimeUnit.SECONDS);
      assertEquals("FORBIDDEN", saving.get(10, TimeUnit.SECONDS).code());
    } finally { releasePlatform.countDown(); }
    assertFalse(Db.bool(db.one("SELECT enabled FROM store WHERE id=?", store), "enabled"));
    assertEquals("平台停用", db.one("SELECT name FROM store WHERE id=?", store).get("name"));
  }

  @Test
  void merchantCannotEnableStoreAndCanSaveDisabledStoreWithoutChangingAvailability() {
    directory.store(store, settings(false, "平台停用"));
    act(manager);
    assertEquals("FORBIDDEN", assertThrows(BusinessException.class,
        () -> directory.store(store, settings(true, "越权启用"))).code());
    directory.store(store, settings(false, "更新营业配置"));
    var row = db.one("SELECT enabled,name FROM store WHERE id=?", store);
    assertFalse(Db.bool(row, "enabled"));
    assertEquals("更新营业配置", row.get("name"));
  }

  private long rejected(String kind) {
    act(customer);
    long request = aftersales.apply(order, new PlatformCommands.Aftersale(kind, "申请处理", null, null));
    act(manager);
    aftersales.decide(request, new PlatformCommands.Decision(0, false, "继续履约"), false);
    act(customer);
    return request;
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 5})
  void rejectedCancellationCannotReserveAgainAfterDeliveryOrCompletion(int status) {
    long request = rejected("CANCEL");
    db.update("UPDATE orders SET status=?,completed_at=? WHERE id=?", status, LocalDateTime.now(clock), order);
    assertEquals("CANCELLATION_UNAVAILABLE", assertThrows(BusinessException.class,
        () -> aftersales.appeal(request, 1)).code());
    var application = db.one("SELECT * FROM aftersale WHERE id=?", request);
    assertEquals("REJECTED", application.get("state"));
    assertFalse(Db.bool(application, "appealed"));
    assertEquals(1, Db.id(application, "version"));
    assertEquals(0, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", order));
    assertEquals(0, db.count("SELECT reserved_quantity FROM order_detail WHERE id=?", detail));
    assertEquals(0, db.count("SELECT COUNT(*) FROM refund_request WHERE order_id=?", order));
    assertEquals(status, Db.integer(aftersales.list(null, false).getFirst(), "order_status"));
  }

  @Test
  void rejectedCancellationStillInPreparationCanBeAppealedAndApproved() {
    long request = rejected("CANCEL");
    aftersales.appeal(request, 1);
    assertEquals(1000, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", order));
    act(platform);
    aftersales.decide(request, new PlatformCommands.Decision(2, true, "同意取消"), true);
    assertEquals(6, db.count("SELECT status FROM orders WHERE id=?", order));
    assertEquals(1, db.count("SELECT COUNT(*) FROM refund_request WHERE aftersale_id=? AND state='REQUESTED'", request));
  }

  @Test
  void completedOrderRefundAppealRetainsItsExistingBehavior() {
    db.update("UPDATE orders SET status=5,completed_at=? WHERE id=?", LocalDateTime.now(clock), order);
    long request = rejected("FULL");
    aftersales.appeal(request, 1);
    assertEquals("ESCALATED", db.one("SELECT state FROM aftersale WHERE id=?", request).get("state"));
    assertEquals(1000, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", order));
  }
}
