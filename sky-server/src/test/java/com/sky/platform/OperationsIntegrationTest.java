package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=operations-test-signing-key-at-least-thirty-two",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false"
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Testcontainers
class OperationsIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("operations_test")
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

  static class MutableClock extends Clock {
    final AtomicReference<Instant> value =
        new AtomicReference<>(Instant.parse("2026-10-26T04:00:00Z"));

    public ZoneId getZone() {
      return ZoneId.of("Asia/Shanghai");
    }

    public Clock withZone(ZoneId z) {
      return this;
    }

    public Instant instant() {
      return value.get();
    }
  }

  @TestConfiguration
  static class Configuration {
    @Bean
    @Primary
    MutableClock operationsClock() {
      return new MutableClock();
    }
  }

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean Db db;
  @Autowired OperationsService operations;
  @Autowired FulfillmentService fulfillment;
  @Autowired PlatformScope scope;
  @Autowired InboxService inbox;
  @Autowired BusinessMessages messages;
  @Autowired CollectionPages pages;
  @Autowired DirectoryService directory;
  @Autowired MutableClock clock;
  @Autowired PlatformTransactionManager manager;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired com.sky.security.AuthService auth;
  @Autowired ReportQueryService reports;
  long campus, store, user, courier, order, task;
  Actor platform = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false);

  private void act(Actor actor) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  private Actor customer() {
    return new Actor(user, "USER", "USER", "", false);
  }

  private Actor rider() {
    return new Actor(courier, "ADMIN", "DELIVERER", "", false, null, campus);
  }

  private void event(String type) {
    new TransactionTemplate(manager).executeWithoutResult(s -> scope.event(order, type));
  }

  @BeforeEach
  void fixture() {
    act(platform);
    clock.value.set(Instant.parse("2026-10-26T04:00:00Z"));
    campus = db.insert("INSERT INTO campus(name) VALUES(?)", "ops-" + UUID.randomUUID());
    store = db.insert("INSERT INTO store(campus_id,name,open) VALUES(?,'操作店',true)", campus);
    user =
        db.insert(
            "INSERT INTO user(openid,name,create_time) VALUES(?,'测试用户',?)",
            UUID.randomUUID().toString(),
            LocalDateTime.now(clock));
    courier = person("DELIVERER");
    order =
        db.insert(
            "INSERT INTO"
                + " orders(number,status,user_id,address_book_id,order_time,amount,store_id,campus_id,paid_cents,pay_status,fulfillment,ready,building_name,phone,address,room,consignee)"
                + " VALUES(?,3,?,0,?,10,?,?,1000,1,'DELIVERY',true,'原楼栋','13800000000','原楼栋101','101','测试联系人')",
            "OPS-" + UUID.randomUUID(),
            user,
            LocalDateTime.now(clock),
            store,
            campus);
    db.insert(
        "INSERT INTO order_detail(order_id,name,number,amount,line_total_cents)"
            + " VALUES(?,'测试菜',1,10,1000)",
        order);
    task = db.insert("INSERT INTO delivery_task(order_id,campus_id) VALUES(?,?)", order, campus);
  }

  long person(String role) {
    return db.insert(
        "INSERT INTO"
            + " employee(name,username,password,role,campus_id,status,must_change_password,phone,sex,id_number)"
            + " VALUES('操作人员',?,'test-only',?,?,1,false,'','','')",
        "ops-" + UUID.randomUUID().toString().substring(0, 12),
        role,
        campus);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void renamedBuildingKeepsSnapshotInPool() {
    long building =
        db.insert("INSERT INTO campus_building(campus_id,name) VALUES(?,'原楼栋')", campus);
    directory.building(campus, building, new com.sky.api.Commands.Building("新楼栋", true));
    act(rider());
    assertEquals("原楼栋", fulfillment.pool().getFirst().get("building_name"));
  }

  @Test
  void stagePolicyIsFrozenAndExceptionsAreIdempotentAndHistorical() {
    db.update("UPDATE orders SET status=2,ready=false WHERE id=?", order);
    event("ORDER_PAID");
    var policy = operations.policy(store);
    operations.policy(store, new OperationsService.Policy(1, 1, 1, 1, 1, 1, policy.version()));
    assertEquals(
        LocalDateTime.now(clock).plusMinutes(10),
        Db.time(
            db.one("SELECT deadline_at FROM order_stage WHERE order_id=?", order)
                .get("deadline_at")));
    clock.value.updateAndGet(i -> i.plusSeconds(601));
    operations.detect(order);
    operations.detect(order);
    assertEquals(1, db.count("SELECT COUNT(*) FROM order_exception WHERE order_id=?", order));
    var e = operations.exceptions(store, null, 50, "OPEN").records().getFirst();
    operations.acknowledge(Long.parseLong(e.id()), new OperationsService.Acknowledge(0, "已联系商家"));
    db.update("UPDATE orders SET status=3 WHERE id=?", order);
    event("ORDER_ACCEPT");
    var history = operations.exceptions(store, null, 50, "RESOLVED").records().getFirst();
    assertEquals("WAIT_ACCEPT", history.stage());
    assertEquals(e.deadlineAt(), history.deadlineAt());
    assertEquals(
        "PREPARING", db.one("SELECT stage FROM order_stage WHERE order_id=?", order).get("stage"));
  }

  @Test
  void durableMessagesAreDeduplicatedAndOwned() {
    event("ORDER_READY");
    long event = db.count("SELECT MAX(id) FROM outbox_event WHERE aggregate_id=?", order);
    new TransactionTemplate(manager)
        .executeWithoutResult(s -> messages.publish(event, order, "ORDER_READY"));
    act(customer());
    assertEquals(1, inbox.unread());
    var message = inbox.list(null, 50, null).records().getFirst();
    inbox.read(Long.parseLong(message.id()));
    inbox.read(Long.parseLong(message.id()));
    assertEquals(0, inbox.unread());
    act(new Actor(user + 100000, "USER", "USER", "", false));
    assertTrue(inbox.list(null, 50, null).records().isEmpty());
    assertEquals(
        404,
        assertThrows(BusinessException.class, () -> inbox.read(Long.parseLong(message.id())))
            .status());
  }

  @Test
  void disabledNewTasksAndNormalHandoverAreEnforced() {
    act(rider());
    operations.availability(courier, false);
    assertEquals(
        "COURIER_UNAVAILABLE",
        assertThrows(BusinessException.class, () -> fulfillment.claim(task, 0)).code());
    operations.availability(courier, true);
    fulfillment.claim(task, 0);
    act(platform);
    var p = db.one("SELECT username FROM employee WHERE id=?", courier);
    assertEquals(
        "COURIER_HANDOVER_REQUIRED",
        assertThrows(
                BusinessException.class,
                () ->
                    directory.staff(
                        courier,
                        new PlatformCommands.Staff(
                            p.get("username").toString(),
                            "测试人员",
                            "DELIVERER",
                            null,
                            campus,
                            false,
                            null)))
            .code());
    operations.emergency(courier, "测试紧急交接");
    operations.detect(order);
    assertEquals(
        1,
        db.count(
            "SELECT COUNT(*) FROM order_exception WHERE order_id=? AND kind='COURIER_UNAVAILABLE'"
                + " AND state='OPEN'",
            order));
    long replacement = person("DELIVERER");
    fulfillment.reassign(order, new PlatformCommands.Reassign(1, replacement, "接管在途任务"));
    assertEquals(
        0,
        db.count(
            "SELECT COUNT(*) FROM order_exception WHERE order_id=? AND state<>'RESOLVED'", order));
    act(rider());
    assertEquals(
        404, assertThrows(BusinessException.class, () -> fulfillment.detail(order)).status());
  }

  @Test
  void courierTerminalProjectionOmitsPrivateAndFinancialData() {
    db.update("UPDATE orders SET courier_id=?,status=4 WHERE id=?", courier, order);
    act(rider());
    var active = fulfillment.detail(order);
    assertEquals("13800000000", active.get("phone"));
    assertFalse(active.containsKey("paid_cents"));
    assertFalse(active.containsKey("aftersales"));
    db.update("UPDATE orders SET status=5 WHERE id=?", order);
    var done = fulfillment.detail(order);
    for (var field : List.of("phone", "address", "room", "consignee", "refunds"))
      assertFalse(done.containsKey(field));
    assertDoesNotThrow(() -> com.sky.web.PlatformViewFactory.courierOrder(done));
  }

  @Test
  void collectionsBeyondTwoHundredAreCompleteAndFilterBound() {
    for (int i = 0; i < 205; i++)
      db.insert(
          "INSERT INTO"
              + " coupon(store_id,name,kind,threshold_cents,value,total,per_user,starts_at,ends_at)"
              + " VALUES(? ,?,'REDUCTION',0,100,999,1,?,?)",
          store,
          "分页券" + i,
          LocalDateTime.now(clock).minusDays(1),
          LocalDateTime.now(clock).plusDays(1));
    var ids = new HashSet<String>();
    String cursor = null;
    do {
      var p = pages.coupons(store, true, new CursorPages.Request(cursor, 50, null, "分页券"));
      p.records().forEach(r -> assertTrue(ids.add(r.id())));
      cursor = p.nextCursor();
    } while (cursor != null);
    assertEquals(205, ids.size());
    var first = pages.coupons(store, true, new CursorPages.Request(null, 50, null, "分页券"));
    assertEquals(
        "INVALID_CURSOR",
        assertThrows(
                BusinessException.class,
                () ->
                    pages.coupons(
                        store, true, new CursorPages.Request(first.nextCursor(), 50, null, "变更筛选")))
            .code());
  }

  @Test
  void favoriteCompositeCursorPreservesAllThreeKinds() {
    for (int i = 1; i <= 205; i++)
      db.update(
          "INSERT INTO favorite(user_id,target_type,target_id,store_id) VALUES(? ,?,?,?)",
          user,
          i % 3 == 0 ? "STORE" : i % 3 == 1 ? "DISH" : "SETMEAL",
          i,
          store);
    act(customer());
    int total = 0;
    String cursor = null;
    var keys = new HashSet<String>();
    do {
      var p = pages.favorites(new CursorPages.Request(cursor, 37, null, null));
      for (var r : p.records())
        assertTrue(keys.add(r.storeId() + ":" + r.targetType() + ":" + r.targetId()));
      total += p.records().size();
      cursor = p.nextCursor();
    } while (cursor != null);
    assertEquals(205, total);
  }

  @Test
  void liveContractDescribesRoleAndFinanceViews() throws Exception {
    var session = auth.login(new com.sky.api.Commands.Login("admin", "TestAdmin!2026"));
    var response =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/v3/api-docs")
                    .header("Authorization", "Bearer " + session.get("token")))
            .andReturn()
            .getResponse();
    assertEquals(200, response.getStatus());
    String source = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(source.contains("CourierOrderResponse"));
    assertTrue(source.contains("FinanceLedgerLine"));
    assertTrue(source.contains("/api/v1/user/auth/capabilities"));
    String destination = System.getProperty("campus.export-openapi");
    if (destination != null) {
      var path = java.nio.file.Path.of(destination).toAbsolutePath();
      java.nio.file.Files.createDirectories(path.getParent());
      java.nio.file.Files.writeString(path, source);
    }
  }

  @Test
  void reportUsesOneSnapshotWhileAnotherPaymentCommits() throws Exception {
    long c =
        db.insert(
            "INSERT INTO"
                + " checkout(user_id,request_id,request_hash,state,total_cents,paid_at,expires_at,created_at,quote_json)"
                + " VALUES(?,? ,?,'PAID',1000,?,?,?,'{}')",
            user,
            UUID.randomUUID().toString(),
            "0".repeat(64),
            LocalDateTime.now(clock),
            LocalDateTime.now(clock).plusMinutes(15),
            LocalDateTime.now(clock));
    db.update("UPDATE orders SET checkout_id=? WHERE id=?", c, order);
    var aggregateRead = new java.util.concurrent.CountDownLatch(1);
    var proceed = new java.util.concurrent.CountDownLatch(1);
    org.mockito.Mockito.doAnswer(
            call -> {
              Object value = call.callRealMethod();
              aggregateRead.countDown();
              assertTrue(proceed.await(10, java.util.concurrent.TimeUnit.SECONDS));
              return value;
            })
        .when(db)
        .one(
            org.mockito.ArgumentMatchers.startsWith("SELECT COUNT(DISTINCT o.checkout_id)"),
            org.mockito.ArgumentMatchers.any(Object[].class));
    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      var result =
          executor.submit(
              () -> {
                act(platform);
                try {
                  return reports.view(campus, store, LocalDate.now(clock), LocalDate.now(clock));
                } finally {
                  clear();
                }
              });
      try {
        assertTrue(aggregateRead.await(10, java.util.concurrent.TimeUnit.SECONDS));
        new TransactionTemplate(manager)
            .executeWithoutResult(
                s -> {
                  long next =
                      db.insert(
                          "INSERT INTO"
                              + " checkout(user_id,request_id,request_hash,state,total_cents,paid_at,expires_at,created_at,quote_json)"
                              + " VALUES(?,? ,?,'PAID',2000,?,?,?,'{}')",
                          user,
                          UUID.randomUUID().toString(),
                          "1".repeat(64),
                          LocalDateTime.now(clock),
                          LocalDateTime.now(clock).plusMinutes(15),
                          LocalDateTime.now(clock));
                  db.insert(
                      "INSERT INTO"
                          + " orders(number,status,user_id,address_book_id,order_time,amount,store_id,campus_id,paid_cents,pay_status,checkout_id)"
                          + " VALUES(?,2,?,0,?,20,?,?,2000,1,?)",
                      "SNAP-" + UUID.randomUUID(),
                      user,
                      LocalDateTime.now(clock),
                      store,
                      campus,
                      next);
                });
      } finally {
        proceed.countDown();
      }
      var snapshot = result.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(1000, snapshot.metrics().paidCents());
      assertEquals(
          1000, snapshot.daily().stream().mapToLong(ReportQueryService.Daily::paidCents).sum());
      assertEquals(
          1000,
          snapshot.stores().stream().mapToLong(ReportQueryService.StoreTotal::paidCents).sum());
    } finally {
      proceed.countDown();
      org.mockito.Mockito.reset(db);
    }
  }

  @Test
  void unassignResolvesUnavailableCourierAndFinancialMessagesStayPrivate() {
    act(rider());
    fulfillment.claim(task, 0);
    act(platform);
    event("ORDER_REFUNDED");
    assertEquals(
        0,
        db.count(
            "SELECT COUNT(*) FROM inbox_message WHERE recipient_type='ADMIN' AND recipient_id=? AND"
                + " event_type='ORDER_REFUNDED'",
            courier));
    operations.emergency(courier, "临时交接");
    operations.detect(order);
    assertEquals(
        1,
        db.count("SELECT COUNT(*) FROM order_exception WHERE order_id=? AND state='OPEN'", order));
    fulfillment.action(order, "unassign", new PlatformCommands.Action(1, null, null, null));
    assertEquals(
        0,
        db.count(
            "SELECT COUNT(*) FROM order_exception WHERE order_id=? AND state<>'RESOLVED'", order));
  }

  @Test
  void staffDisableSerializesWithConcurrentClaim() throws Exception {
    var counted = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var claimStarted = new java.util.concurrent.CountDownLatch(1);
    var claimFinished = new java.util.concurrent.CountDownLatch(1);
    org.mockito.Mockito.doAnswer(
            call -> {
              Object result = call.callRealMethod();
              counted.countDown();
              assertTrue(release.await(10, java.util.concurrent.TimeUnit.SECONDS));
              return result;
            })
        .when(db)
        .count("SELECT COUNT(*) FROM orders WHERE courier_id=? AND status IN(3,4)", courier);
    org.mockito.Mockito.doAnswer(
            call -> {
              claimStarted.countDown();
              return call.callRealMethod();
            })
        .when(db)
        .one("SELECT * FROM employee WHERE id=? FOR UPDATE", courier);
    String username =
        db.one("SELECT username FROM employee WHERE id=?", courier).get("username").toString();
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var disabling =
          workers.submit(
              () -> {
                act(platform);
                try {
                  directory.staff(
                      courier,
                      new PlatformCommands.Staff(
                          username, "测试配送员", "DELIVERER", null, campus, false, null));
                } finally {
                  clear();
                }
              });
      assertTrue(counted.await(10, java.util.concurrent.TimeUnit.SECONDS));
      var claiming =
          workers.submit(
              () -> {
                act(rider());
                try {
                  return assertThrows(BusinessException.class, () -> fulfillment.claim(task, 0));
                } finally {
                  claimFinished.countDown();
                  clear();
                }
              });
      try {
        assertTrue(claimStarted.await(10, java.util.concurrent.TimeUnit.SECONDS));
        assertFalse(claimFinished.await(300, java.util.concurrent.TimeUnit.MILLISECONDS));
      } finally {
        release.countDown();
      }
      disabling.get(10, java.util.concurrent.TimeUnit.SECONDS);
      assertEquals(
          "COURIER_UNAVAILABLE", claiming.get(10, java.util.concurrent.TimeUnit.SECONDS).code());
      assertEquals("OPEN", db.one("SELECT state FROM delivery_task WHERE id=?", task).get("state"));
    } finally {
      release.countDown();
      org.mockito.Mockito.reset(db);
    }
  }
}
