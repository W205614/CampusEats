package com.sky;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sky.api.Commands;
import com.sky.business.*;
import com.sky.common.BusinessException;
import com.sky.infra.*;
import com.sky.platform.*;
import com.sky.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false"
    })
@AutoConfigureMockMvc
@Testcontainers
class PlatformIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("platform_test")
          .withUsername("test")
          .withPassword("test")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  static class TestClock extends Clock {
    AtomicReference<Instant> value = new AtomicReference<>(Instant.parse("2026-10-15T04:00:00Z"));

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
  static class Config {
    @Bean
    @Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  @Autowired Db db;
  @Autowired AuthService auth;
  @Autowired StoreCart cart;
  @Autowired CheckoutService checkout;
  @Autowired MarketingService marketing;
  @Autowired FulfillmentService fulfillment;
  @Autowired AftersaleService aftersale;
  @Autowired PlatformJobs jobs;
  @Autowired DirectoryService directory;
  @Autowired StoreCatalog catalog;
  @Autowired DiscoveryService discovery;
  @Autowired EvidenceService evidence;
  @Autowired MockPaymentGateway gateway;
  @Autowired MockMvc mvc;
  @Autowired Json json;
  @Autowired TestClock clock;
  @Autowired com.sky.infra.MaintenanceJobs maintenance;
  @Autowired com.sky.infra.RuntimeOperations operations;
  @Autowired com.sky.infra.OperationalMetrics operationalMetrics;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path evidenceRoot;
  long user, dish3;
  Actor platform = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false);

  void act(Actor actor) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  Actor user(long id) {
    return new Actor(id, "USER", "USER", "", false);
  }

  Actor staff(String name) {
    var e = db.one("SELECT * FROM employee WHERE username=?", name);
    return new Actor(
        Db.id(e, "id"),
        "ADMIN",
        e.get("role").toString(),
        "",
        false,
        e.get("store_id") == null ? null : Db.id(e, "store_id"),
        e.get("campus_id") == null ? null : Db.id(e, "campus_id"));
  }

  @BeforeEach
  void reset() {
    clock.value.set(Instant.parse("2026-10-15T04:00:00Z"));
    for (String table :
        List.of(
            "evidence",
            "aftersale_item",
            "refund_request",
            "aftersale",
            "delivery_task",
            "review",
            "favorite",
            "user_coupon",
            "coupon",
            "promotion",
            "order_reservation",
            "payment_attempt",
            "refund_task",
            "outbox_event",
            "audit_log",
            "order_detail",
            "orders",
            "checkout",
            "store_cart_item",
            "store_cart_state",
            "address_book",
            "auth_session",
            "daily_quota",
            "user")) db.update("DELETE FROM " + table);
    db.update(
        "UPDATE store SET"
            + " enabled=true,open=true,hours='[\"00:00-24:00\"]',delivery_fee=6,packaging_fee=1");
    db.update("UPDATE campus SET enabled=true");
    db.update("UPDATE dish SET status=1,default_quota=100");
    db.update("UPDATE category SET status=1");
    user = Long.parseLong(auth.demo(new Commands.DemoLogin(1)).get("id").toString());
    dish3 = db.count("SELECT MIN(id) FROM dish WHERE store_id=3");
    act(user(user));
    gateway.failNextPayments(0);
    gateway.failNextRefunds(0);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void add(long store, long dish, int quantity) {
    var v = cart.view(store);
    cart.change(
        store,
        new PlatformCommands.CartChange(Db.id(v, "cartVersion"), "DISH", dish, Map.of(), quantity));
  }

  PlatformCommands.Checkout input() {
    return new PlatformCommands.Checkout(
        List.of(
            new PlatformCommands.Selection(
                1, Db.id(cart.view(1), "cartVersion"), "PICKUP", null, null),
            new PlatformCommands.Selection(
                3, Db.id(cart.view(3), "cartVersion"), "PICKUP", null, null)),
        null,
        null,
        "测试合单");
  }

  PlatformCommands.Checkout quoted(PlatformCommands.Checkout input) {
    var q = checkout.preview(input);
    return new PlatformCommands.Checkout(
        input.stores(), input.platformCouponId(), q.quoteHash(), input.remark());
  }

  Map<String, Object> submit() {
    add(1, 2, 2);
    add(3, dish3, 1);
    return checkout.submit(UUID.randomUUID().toString(), quoted(input()));
  }

  long paidOrder() {
    var c = submit();
    checkout.pay(Db.id(c, "id"), 0);
    return db.count("SELECT id FROM orders WHERE checkout_id=? AND store_id=1", c.get("id"));
  }

  long completed() {
    long id = paidOrder();
    act(platform);
    fulfillment.action(id, "accept", new PlatformCommands.Action(1, null, null, null));
    fulfillment.action(id, "ready", new PlatformCommands.Action(2, null, null, null));
    act(user(user));
    String code =
        db.one("SELECT pickup_code FROM orders WHERE id=?", id).get("pickup_code").toString();
    act(platform);
    assertEquals(
        true,
        fulfillment.verify(id, new PlatformCommands.Action(3, null, null, code)).get("verified"));
    act(user(user));
    return id;
  }

  @Test
  void independentCartsAndAllOrNothingSubmission() {
    add(1, 2, 1);
    add(3, dish3, 1);
    var body = quoted(input());
    db.update("UPDATE dish SET default_quota=0 WHERE id=?", dish3);
    assertThrows(
        BusinessException.class, () -> checkout.submit(UUID.randomUUID().toString(), body));
    assertEquals(0, db.count("SELECT COUNT(*) FROM checkout"));
    assertEquals(0, db.count("SELECT COUNT(*) FROM order_reservation"));
    assertEquals(2, db.count("SELECT COUNT(*) FROM store_cart_item"));
  }

  @Test
  void idempotentCombinedPaymentAndConflict() {
    add(1, 2, 1);
    add(3, dish3, 1);
    var body = quoted(input());
    String key = UUID.randomUUID().toString();
    var c = checkout.submit(key, body);
    assertEquals(c.get("id"), checkout.submit(key, body).get("id"));
    assertThrows(
        BusinessException.class,
        () ->
            checkout.submit(
                key,
                new PlatformCommands.Checkout(body.stores(), null, body.quoteHash(), "different")));
    gateway.failNextPayments(1);
    assertThrows(BusinessException.class, () -> checkout.pay(Db.id(c, "id"), 0));
    assertEquals(2, db.count("SELECT COUNT(*) FROM orders WHERE status=1"));
    checkout.pay(Db.id(c, "id"), 0);
    checkout.pay(Db.id(c, "id"), 0);
    assertEquals(2, db.count("SELECT COUNT(*) FROM payment_attempt"));
    assertEquals(2, db.count("SELECT COUNT(*) FROM orders WHERE status=2"));
  }

  @Test
  void expirationCancelsWholeBatchAndReleasesQuota() {
    var c = submit();
    clock.value.set(clock.instant().plus(Duration.ofMinutes(16)));
    checkout.expire(Db.id(c, "id"));
    assertEquals(2, db.count("SELECT COUNT(*) FROM orders WHERE status=6"));
    assertEquals(0, db.count("SELECT COALESCE(SUM(reserved),0) FROM daily_quota"));
  }

  @Test
  void storeAndAddressIsolation() {
    act(staff("manager1"));
    assertThrows(BusinessException.class, () -> catalog.products(3, true, null, null));
    assertThrows(BusinessException.class, () -> directory.staff(3L));
    act(user(user));
    assertThrows(
        BusinessException.class,
        () -> cart.change(1, new PlatformCommands.CartChange(0, "DISH", dish3, Map.of(), 1)));
  }

  @Test
  void partialRefundPreservesOtherStoreAndRetries() {
    long order = completed();
    long detail = db.count("SELECT id FROM order_detail WHERE order_id=?", order);
    long request =
        aftersale.apply(
            order,
            new PlatformCommands.Aftersale(
                "PARTIAL", "少了一份", List.of(new PlatformCommands.RefundLine(detail, 1)), null));
    assertThrows(
        BusinessException.class,
        () ->
            aftersale.apply(
                order,
                new PlatformCommands.Aftersale(
                    "PARTIAL", "重复", List.of(new PlatformCommands.RefundLine(detail, 2)), null)));
    act(platform);
    aftersale.decide(request, new PlatformCommands.Decision(0, true, "同意"), false);
    gateway.failNextRefunds(1);
    assertTrue(jobs.refund());
    assertEquals(0, db.count("SELECT refunded_cents FROM orders WHERE id=?", order));
    clock.value.set(clock.instant().plusSeconds(10));
    assertTrue(jobs.refund());
    assertEquals(1200, db.count("SELECT refunded_cents FROM orders WHERE id=?", order));
    assertEquals(5, db.count("SELECT status FROM orders WHERE id=?", order));
    assertEquals(0, db.count("SELECT refunded_cents FROM orders WHERE store_id=3"));
    act(user(user));
    long second = aftersale.apply(order, new PlatformCommands.Aftersale("FULL", "退剩余", null, null));
    act(platform);
    aftersale.decide(second, new PlatformCommands.Decision(0, true, "同意"), false);
    assertTrue(jobs.refund());
    assertEquals(2600, db.count("SELECT refunded_cents FROM orders WHERE id=?", order));
    assertEquals(0, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", order));
    assertEquals(2, db.count("SELECT pay_status FROM orders WHERE id=?", order));
  }

  @Test
  void rejectReleaseAndPlatformIntervention() {
    long id = completed();
    long request = aftersale.apply(id, new PlatformCommands.Aftersale("FULL", "质量问题", null, null));
    act(platform);
    aftersale.decide(request, new PlatformCommands.Decision(0, false, "拒绝"), false);
    assertEquals(0, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", id));
    act(user(user));
    aftersale.appeal(request, 1);
    act(platform);
    aftersale.decide(request, new PlatformCommands.Decision(2, true, "平台同意"), true);
    jobs.refund();
    assertEquals(2600, db.count("SELECT refunded_cents FROM orders WHERE id=?", id));
  }

  @Test
  void wrongPickupAttemptsPersistAndCodeCannotReplay() {
    long id = paidOrder();
    act(platform);
    fulfillment.action(id, "accept", new PlatformCommands.Action(1, null, null, null));
    fulfillment.action(id, "ready", new PlatformCommands.Action(2, null, null, null));
    for (int i = 0; i < 5; i++)
      assertEquals(
          false,
          fulfillment
              .verify(id, new PlatformCommands.Action(3, null, null, "wrong"))
              .get("verified"));
    assertThrows(
        BusinessException.class,
        () -> fulfillment.verify(id, new PlatformCommands.Action(3, null, null, "wrong")));
    fulfillment.resetCode(id);
    String code =
        db.one("SELECT pickup_code FROM orders WHERE id=?", id).get("pickup_code").toString();
    fulfillment.verify(id, new PlatformCommands.Action(4, null, null, code));
    assertThrows(
        BusinessException.class,
        () -> fulfillment.verify(id, new PlatformCommands.Action(4, null, null, code)));
  }

  @Test
  void oldSingleStoreEndpointsRetiredAndEvidencePrivate() throws Exception {
    mvc.perform(get("/api/v1/user/shop")).andExpect(status().isGone());
    mvc.perform(post("/api/v1/user/orders")).andExpect(status().isGone());
    mvc.perform(get("/api/v1/user/orders/by-request/old-request")).andExpect(status().isGone());
    mvc.perform(get("/uploads/.private/anything.png")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/user/campuses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2));
  }

  @Test
  void exportCurrentOpenApiContract() throws Exception {
    String token =
        auth.login(new Commands.Login("admin", "TestAdmin!2026")).get("token").toString();
    String spec =
        mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    var contract = json.map(spec);
    assertLongParameter(contract, "/api/v1/user/orders", "beforeId", "query");
    assertLongParameter(contract, "/api/v1/user/orders/{id}", "id", "path");
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/platform-openapi.json"), spec);
  }

  private void assertLongParameter(
      Map<String, Object> contract, String path, String name, String location) {
    Map<?, ?> paths = assertInstanceOf(Map.class, contract.get("paths"));
    Map<?, ?> pathItem = assertInstanceOf(Map.class, paths.get(path));
    Map<?, ?> operation = assertInstanceOf(Map.class, pathItem.get("get"));
    List<?> parameters = assertInstanceOf(List.class, operation.get("parameters"));
    Map<?, ?> parameter =
        parameters.stream()
            .filter(value -> value instanceof Map<?, ?> map && name.equals(map.get("name")))
            .map(value -> (Map<?, ?>) value)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing parameter " + path + ": " + name));
    assertEquals(location, parameter.get("in"));
    Map<?, ?> schema = assertInstanceOf(Map.class, parameter.get("schema"));
    assertEquals("string", schema.get("type"));
    assertEquals("^[0-9]+$", schema.get("pattern"));
  }

  @Test
  void couponsStackWithActivitiesAndNoRoundingLoss() {
    act(platform);
    marketing.promotion(
        1,
        null,
        new PlatformCommands.Promotion(
            "DISH",
            2,
            1001,
            LocalDateTime.now(clock).minusHours(1),
            LocalDateTime.now(clock).plusHours(1),
            true));
    long coupon =
        marketing.coupon(
            1L,
            null,
            new PlatformCommands.Coupon(
                "店券",
                "REDUCTION",
                0,
                101,
                null,
                10,
                1,
                LocalDateTime.now(clock).minusHours(1),
                LocalDateTime.now(clock).plusHours(1),
                true));
    long platformCoupon =
        marketing.coupon(
            null,
            null,
            new PlatformCommands.Coupon(
                "平台券",
                "DISCOUNT",
                0,
                9000,
                null,
                10,
                1,
                LocalDateTime.now(clock).minusHours(1),
                LocalDateTime.now(clock).plusHours(1),
                true));
    act(user(user));
    long owned = marketing.claim(coupon), platformOwned = marketing.claim(platformCoupon);
    add(1, 2, 2);
    add(3, dish3, 1);
    var in = input();
    var selections = new ArrayList<>(in.stores());
    var first = selections.getFirst();
    selections.set(
        0, new PlatformCommands.Selection(1, first.cartVersion(), "PICKUP", null, owned));
    var body = quoted(new PlatformCommands.Checkout(selections, platformOwned, null, null));
    var q = checkout.preview(body);
    assertEquals(
        q.totalCents(),
        q.stores().stream()
            .mapToLong(
                p ->
                    p.items().stream().mapToLong(CheckoutService.Line::paidCents).sum()
                        + p.packagingCents()
                        + p.deliveryCents())
            .sum());
    var c = checkout.submit(UUID.randomUUID().toString(), body);
    checkout.pay(Db.id(c, "id"), 0);
    assertEquals(2, db.count("SELECT COUNT(*) FROM user_coupon WHERE state='USED'"));
    assertThrows(BusinessException.class, () -> marketing.claim(coupon));
  }

  @Test
  void concurrentCourierClaimHasOneWinner() throws Exception {
    long order = paidOrder();
    db.update("UPDATE orders SET fulfillment='DELIVERY' WHERE id=?", order);
    act(platform);
    fulfillment.action(order, "accept", new PlatformCommands.Action(1, null, null, null));
    fulfillment.action(order, "ready", new PlatformCommands.Action(2, null, null, null));
    fulfillment.action(order, "publish", new PlatformCommands.Action(3, null, null, null));
    long task = db.count("SELECT id FROM delivery_task WHERE order_id=?", order);
    Actor a = staff("courier1"), b = staff("courier1b");
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    var success = new AtomicInteger();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<?>>();
      for (var courier : List.of(a, b))
        futures.add(
            pool.submit(
                () -> {
                  act(courier);
                  ready.countDown();
                  try {
                    start.await();
                    fulfillment.claim(task, 0);
                    success.incrementAndGet();
                  } catch (BusinessException expected) {
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    clear();
                  }
                }));
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      for (var f : futures) f.get(20, TimeUnit.SECONDS);
    }
    assertEquals(1, success.get());
    act(staff("courier2"));
    assertThrows(BusinessException.class, () -> fulfillment.claim(task, 1));
  }

  @Test
  void courierCampusChangeRevokesOldCampusOrderVisibility() {
    long order = paidOrder();
    db.update("UPDATE orders SET fulfillment='DELIVERY' WHERE id=?", order);
    act(platform);
    fulfillment.action(order, "accept", new PlatformCommands.Action(1, null, null, null));
    fulfillment.action(order, "ready", new PlatformCommands.Action(2, null, null, null));
    Actor courier = staff("courier1");
    fulfillment.action(order, "assign", new PlatformCommands.Action(3, null, courier.id(), null));
    act(courier);
    assertEquals(1, fulfillment.orders(null).size());
    assertEquals(order, Db.id(fulfillment.detail(order), "id"));

    // A newly issued actor after a scope change must lose both list and detail access.
    Actor transferred = new Actor(courier.id(), "ADMIN", "DELIVERER", "", false, null, 2L);
    act(transferred);
    assertTrue(fulfillment.orders(null).isEmpty());
    assertThrows(BusinessException.class, () -> fulfillment.detail(order));
    assertTrue(fulfillment.pool().isEmpty());
  }

  @Test
  void administratorsCannotConcurrentlyDisableEachOther() throws Exception {
    act(platform);
    long second =
        directory.staff(
            null,
            new PlatformCommands.Staff(
                "scope-test-admin", "第二管理员", "PLATFORM_ADMIN", null, null, true, "TestAdmin!2026"));
    Actor other = new Actor(second, "ADMIN", "PLATFORM_ADMIN", "", false);
    try {
      var result =
          race(
              List.of(platform, other),
              actor -> {
                boolean first = actor.id() == platform.id();
                directory.staff(
                    first ? second : platform.id(),
                    new PlatformCommands.Staff(
                        first ? "scope-test-admin" : "admin",
                        "平台管理员",
                        "PLATFORM_ADMIN",
                        null,
                        null,
                        false,
                        null));
              });
      assertEquals(1, result.successes());
      assertEquals(List.of("LAST_PLATFORM_ADMIN"), result.failures());
      assertEquals(
          1, db.count("SELECT COUNT(*) FROM employee WHERE role='PLATFORM_ADMIN' AND status=1"));
    } finally {
      db.update("UPDATE employee SET status=1 WHERE id=?", platform.id());
      db.update("DELETE FROM employee WHERE id=?", second);
    }
  }

  long assignedDelivery(boolean picked) {
    long id = paidOrder();
    db.update("UPDATE orders SET fulfillment='DELIVERY' WHERE id=?", id);
    act(platform);
    fulfillment.action(id, "accept", new PlatformCommands.Action(1, null, null, null));
    fulfillment.action(id, "ready", new PlatformCommands.Action(2, null, null, null));
    Actor courier = staff("courier1");
    fulfillment.action(id, "assign", new PlatformCommands.Action(3, null, courier.id(), null));
    if (picked) {
      act(courier);
      fulfillment.action(id, "pickup", new PlatformCommands.Action(4, null, null, null));
    }
    act(platform);
    return id;
  }

  @Test
  void platformReassignRecoversDisabledCourierAndPreservesPickedClock() {
    long id = assignedDelivery(true);
    Actor old = staff("courier1"), replacement = staff("courier1b");
    var before = db.one("SELECT * FROM orders WHERE id=?", id);
    db.update("UPDATE orders SET delivery_overdue=true WHERE id=?", id);
    db.update("UPDATE employee SET status=0,campus_id=2 WHERE id=?", old.id());
    try {
      String reason = "改".repeat(200);
      var changed =
          fulfillment.reassign(id, new PlatformCommands.Reassign(5, replacement.id(), reason));
      assertEquals(4, Db.integer(changed, "status"));
      assertEquals(replacement.id(), Db.id(changed, "courier_id"));
      assertEquals(before.get("delivery_started_at"), changed.get("delivery_started_at"));
      assertTrue(Db.bool(changed, "delivery_overdue"));
      assertEquals(
          "PICKED", db.one("SELECT state FROM delivery_task WHERE order_id=?", id).get("state"));
      assertEquals(
          1,
          db.count(
              "SELECT COUNT(*) FROM audit_log WHERE action='ORDER_REASSIGN' AND target_id=? AND"
                  + " detail=?",
              id,
              reason));
      assertEquals(
          1,
          db.count(
              "SELECT COUNT(*) FROM audit_log WHERE action='DELIVERY_REASSIGN' AND target_id=? AND"
                  + " detail=?",
              id,
              "courier=" + old.id() + "->" + replacement.id() + ";state=PICKED"));
      act(old);
      assertThrows(
          BusinessException.class,
          () ->
              fulfillment.action(id, "complete", new PlatformCommands.Action(6, null, null, null)));
      act(replacement);
      fulfillment.action(id, "complete", new PlatformCommands.Action(6, null, null, null));
      assertEquals(5, db.count("SELECT status FROM orders WHERE id=?", id));
    } finally {
      db.update("UPDATE employee SET status=1,campus_id=1 WHERE id=?", old.id());
    }
  }

  @Test
  void platformReassignBeforePickupValidatesRoleCampusStatusReasonAndVersion() {
    long id = assignedDelivery(false);
    long replacement = staff("courier1b").id();
    for (Actor actor : List.of(staff("manager1"), staff("courier1"), user(user))) {
      act(actor);
      assertEquals(
          "FORBIDDEN",
          assertThrows(
                  BusinessException.class,
                  () ->
                      fulfillment.reassign(id, new PlatformCommands.Reassign(4, replacement, "改派")))
              .code());
    }
    act(platform);
    for (long invalid : List.of(staff("courier1").id(), staff("courier2").id(), platform.id()))
      assertEquals(
          "INVALID_COURIER",
          assertThrows(
                  BusinessException.class,
                  () -> fulfillment.reassign(id, new PlatformCommands.Reassign(4, invalid, "改派")))
              .code());
    db.update("UPDATE employee SET status=0 WHERE id=?", replacement);
    try {
      assertEquals(
          "INVALID_COURIER",
          assertThrows(
                  BusinessException.class,
                  () ->
                      fulfillment.reassign(id, new PlatformCommands.Reassign(4, replacement, "改派")))
              .code());
    } finally {
      db.update("UPDATE employee SET status=1 WHERE id=?", replacement);
    }
    assertEquals(
        "REASON_REQUIRED",
        assertThrows(
                BusinessException.class,
                () -> fulfillment.reassign(id, new PlatformCommands.Reassign(4, replacement, "  ")))
            .code());
    assertEquals(
        "INVALID_REASON",
        assertThrows(
                BusinessException.class,
                () ->
                    fulfillment.reassign(
                        id, new PlatformCommands.Reassign(4, replacement, "改".repeat(201))))
            .code());
    assertEquals(
        "VERSION_CONFLICT",
        assertThrows(
                BusinessException.class,
                () -> fulfillment.reassign(id, new PlatformCommands.Reassign(3, replacement, "改派")))
            .code());
    fulfillment.reassign(id, new PlatformCommands.Reassign(4, replacement, "原配送员不可用"));
    assertEquals(
        "ASSIGNED", db.one("SELECT state FROM delivery_task WHERE order_id=?", id).get("state"));
    assertNull(
        db.one("SELECT delivery_started_at FROM orders WHERE id=?", id).get("delivery_started_at"));
    act(staff("courier1b"));
    fulfillment.action(id, "pickup", new PlatformCommands.Action(5, null, null, null));
  }

  @Test
  void exceptionCancelReservesRemainingOnceWithoutChangingOtherStoreOrConsumption() {
    long id = assignedDelivery(true);
    var before = db.one("SELECT * FROM orders WHERE id=?", id);
    var other =
        db.one("SELECT * FROM orders WHERE checkout_id=? AND id<>?", before.get("checkout_id"), id);
    long coupon =
        db.insert(
            "INSERT INTO"
                + " coupon(name,kind,threshold_cents,value,total,per_user,starts_at,ends_at,enabled)"
                + " VALUES('已用测试券','REDUCTION',0,100,1,1,?,?,true)",
            LocalDateTime.now(clock).minusDays(1),
            LocalDateTime.now(clock).plusDays(1));
    db.update(
        "INSERT INTO user_coupon(user_id,coupon_id,state,checkout_id) VALUES(?,?,'USED',?)",
        user,
        coupon,
        before.get("checkout_id"));
    var quotasBefore = db.rows("SELECT * FROM daily_quota ORDER BY business_date,dish_id");
    var couponsBefore = db.rows("SELECT * FROM user_coupon ORDER BY id");
    long paid = Db.id(before, "paid_cents");
    db.update("UPDATE orders SET refunded_cents=100 WHERE id=?", id);
    fulfillment.exceptionCancel(id, new PlatformCommands.ExceptionCancel(5, "配送中断，平台确认取消"));
    assertEquals(6, db.count("SELECT status FROM orders WHERE id=?", id));
    assertEquals(
        "CANCELLED", db.one("SELECT state FROM delivery_task WHERE order_id=?", id).get("state"));
    assertEquals(paid - 100, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", id));
    assertEquals(
        paid - 100, db.count("SELECT amount_cents FROM refund_request WHERE order_id=?", id));
    assertEquals(
        "VERSION_CONFLICT",
        assertThrows(
                BusinessException.class,
                () ->
                    fulfillment.exceptionCancel(id, new PlatformCommands.ExceptionCancel(5, "重复")))
            .code());
    assertEquals(1, db.count("SELECT COUNT(*) FROM refund_request WHERE order_id=?", id));
    assertEquals(other, db.one("SELECT * FROM orders WHERE id=?", other.get("id")));
    assertEquals(quotasBefore, db.rows("SELECT * FROM daily_quota ORDER BY business_date,dish_id"));
    assertEquals(couponsBefore, db.rows("SELECT * FROM user_coupon ORDER BY id"));
    assertTrue(jobs.refund());
    assertEquals(paid, db.count("SELECT refunded_cents FROM orders WHERE id=?", id));
  }

  @Test
  void exceptionCancelRejectsOrdinaryRolesUnfinishedAftersaleAndReservedRefund() {
    long id = assignedDelivery(false);
    for (Actor actor : List.of(user(user), staff("manager1"), staff("courier1"))) {
      act(actor);
      assertEquals(
          "FORBIDDEN",
          assertThrows(
                  BusinessException.class,
                  () ->
                      fulfillment.exceptionCancel(
                          id, new PlatformCommands.ExceptionCancel(4, "取消")))
              .code());
    }
    act(user(user));
    long request =
        aftersale.apply(id, new PlatformCommands.Aftersale("CANCEL", "无法取餐", null, null));
    act(platform);
    assertEquals(
        "AFTERSALE_PENDING",
        assertThrows(
                BusinessException.class,
                () ->
                    fulfillment.exceptionCancel(id, new PlatformCommands.ExceptionCancel(4, "取消")))
            .code());
    db.update("UPDATE aftersale SET state='WITHDRAWN' WHERE id=?", request);
    db.update("UPDATE orders SET reserved_refund_cents=1 WHERE id=?", id);
    assertEquals(
        "AFTERSALE_PENDING",
        assertThrows(
                BusinessException.class,
                () ->
                    fulfillment.exceptionCancel(id, new PlatformCommands.ExceptionCancel(4, "取消")))
            .code());
    db.update("UPDATE orders SET reserved_refund_cents=0,refunded_cents=paid_cents WHERE id=?", id);
    fulfillment.exceptionCancel(id, new PlatformCommands.ExceptionCancel(4, "无需再退款"));
    assertEquals(0, db.count("SELECT COUNT(*) FROM refund_request WHERE order_id=?", id));
  }

  @Test
  void concurrentCompleteReassignAndExceptionCancelHaveOneWinner() throws Exception {
    long id = assignedDelivery(true);
    Actor original = staff("courier1");
    long replacement = staff("courier1b").id();
    var phase = new AtomicInteger();
    Actor otherAdmin = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "second-command", false);
    var result =
        race(
            List.of(original, platform, otherAdmin),
            actor -> {
              if (actor.role().equals("DELIVERER"))
                fulfillment.action(
                    id, "complete", new PlatformCommands.Action(5, null, null, null));
              else if (phase.getAndIncrement() == 0)
                fulfillment.reassign(id, new PlatformCommands.Reassign(5, replacement, "异常改派"));
              else fulfillment.exceptionCancel(id, new PlatformCommands.ExceptionCancel(5, "配送中断"));
            });
    assertEquals(1, result.successes());
    assertEquals(2, result.failures().size());
    assertEquals(6, db.count("SELECT version FROM orders WHERE id=?", id));
    assertTrue(db.count("SELECT COUNT(*) FROM refund_request WHERE order_id=?", id) <= 1);
  }

  @Test
  void exceptionEndpointsEnforceValidationAuthorizationAndVersion() throws Exception {
    long id = assignedDelivery(false);
    long replacement = staff("courier1b").id();
    String adminToken =
        auth.login(new Commands.Login("admin", "TestAdmin!2026")).get("token").toString();
    String userToken = auth.demo(new Commands.DemoLogin(1)).get("token").toString();
    String path = "/api/v1/platform/orders/" + id;
    String body = json.write(Map.of("version", 4, "courierId", replacement, "reason", "平台处理"));
    mvc.perform(
            post(path + "/reassign")
                .header("Authorization", "Bearer " + userToken)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path + "/reassign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(json.write(Map.of("version", 4, "reason", "   "))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path + "/reassign")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk());
    mvc.perform(
            post(path + "/exception-cancel")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(json.write(Map.of("version", 4, "reason", "取消"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    mvc.perform(
            post(path + "/exception-cancel")
                .header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .content(json.write(Map.of("version", 5, "reason", "配送无法完成"))))
        .andExpect(status().isOk());
    assertEquals(1, db.count("SELECT COUNT(*) FROM refund_request WHERE order_id=?", id));
  }

  @Test
  void operationsHealthExposesDisabledJobsAndMetricsWithoutAffectingReadiness() throws Exception {
    operationalMetrics.sample();
    mvc.perform(get("/actuator/health/operations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.runtimeOperations.details.tasksEnabled").value(false))
        .andExpect(
            jsonPath("$.components.runtimeOperations.details.jobs.checkoutExpiry.status")
                .value("DISABLED"))
        .andExpect(
            jsonPath("$.components.runtimeOperations.details.jobs.maintenance.enabled")
                .value(false))
        .andExpect(
            jsonPath("$.components.runtimeOperations.details.jobs.metrics.enabled").value(true))
        .andExpect(
            jsonPath("$.components.runtimeOperations.details.jobs.metrics.status").value("OK"));
    try {
      operations.run(
          "metrics", round -> round.failed(new IllegalStateException("test failure"), "test"));
      mvc.perform(get("/actuator/health/operations")).andExpect(status().isServiceUnavailable());
      String readiness =
          mvc.perform(get("/actuator/health/readiness"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertFalse(readiness.contains("runtimeOperations"));
    } finally {
      operationalMetrics.sample();
    }
  }

  record Race(int successes, List<String> failures) {}

  Race race(List<Actor> actors, java.util.function.Consumer<Actor> task) throws Exception {
    var ready = new CountDownLatch(actors.size());
    var start = new CountDownLatch(1);
    var successes = new AtomicInteger();
    var failures = new ConcurrentLinkedQueue<String>();
    try (var pool = Executors.newFixedThreadPool(actors.size())) {
      var futures = new ArrayList<Future<?>>();
      for (var actor : actors)
        futures.add(
            pool.submit(
                () -> {
                  act(actor);
                  ready.countDown();
                  try {
                    start.await();
                    task.accept(actor);
                    successes.incrementAndGet();
                  } catch (BusinessException e) {
                    failures.add(e.code());
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                  } finally {
                    clear();
                  }
                }));
      assertTrue(ready.await(10, TimeUnit.SECONDS));
      start.countDown();
      for (var future : futures) future.get(20, TimeUnit.SECONDS);
    }
    return new Race(successes.get(), List.copyOf(failures));
  }

  PlatformCommands.Checkout oneInput(long store) {
    return new PlatformCommands.Checkout(
        List.of(
            new PlatformCommands.Selection(
                store, Db.id(cart.view(store), "cartVersion"), "PICKUP", null, null)),
        null,
        null,
        null);
  }

  @Test
  void concurrentDuplicateSubmitCreatesOnlyOneBatch() throws Exception {
    add(1, 2, 1);
    var body = quoted(oneInput(1));
    String key = UUID.randomUUID().toString();
    var result = race(List.of(user(user), user(user)), a -> checkout.submit(key, body));
    assertEquals(2, result.successes());
    assertEquals(1, db.count("SELECT COUNT(*) FROM checkout"));
    assertEquals(1, db.count("SELECT SUM(reserved) FROM daily_quota"));
  }

  @Test
  void concurrentLastQuotaCannotOversell() throws Exception {
    long second = Long.parseLong(auth.demo(new Commands.DemoLogin(2)).get("id").toString());
    db.update("UPDATE dish SET default_quota=1 WHERE id=2");
    add(1, 2, 1);
    var first = quoted(oneInput(1));
    act(user(second));
    add(1, 2, 1);
    var other = quoted(oneInput(1));
    var result =
        race(
            List.of(user(user), user(second)),
            a -> checkout.submit(UUID.randomUUID().toString(), a.id() == user ? first : other));
    assertEquals(1, result.successes());
    assertEquals(List.of("SOLD_OUT"), result.failures());
    assertEquals(1, db.count("SELECT SUM(reserved) FROM daily_quota"));
    assertEquals(1, db.count("SELECT COUNT(*) FROM checkout"));
  }

  @Test
  void concurrentCouponClaimCannotExceedIssuance() throws Exception {
    long second = Long.parseLong(auth.demo(new Commands.DemoLogin(2)).get("id").toString());
    act(platform);
    long id =
        marketing.coupon(
            null,
            null,
            new PlatformCommands.Coupon(
                "最后一张",
                "REDUCTION",
                0,
                100,
                null,
                1,
                1,
                LocalDateTime.now(clock).minusHours(1),
                LocalDateTime.now(clock).plusDays(1),
                true));
    var result = race(List.of(user(user), user(second)), a -> marketing.claim(id));
    assertEquals(1, result.successes());
    assertEquals(List.of("COUPON_SOLD_OUT"), result.failures());
    assertEquals(1, db.count("SELECT claimed FROM coupon WHERE id=?", id));
  }

  @Test
  void concurrentPromotionCreationHasOneNonOverlappingWinner() throws Exception {
    var body =
        new PlatformCommands.Promotion(
            "DISH", 2, 1000, LocalDateTime.now(clock), LocalDateTime.now(clock).plusHours(1), true);
    var result = race(List.of(platform, platform), a -> marketing.promotion(1, null, body));
    assertEquals(1, result.successes());
    assertEquals(List.of("PROMOTION_OVERLAP"), result.failures());
  }

  @Test
  void allGoodsPartialRefundThenRemainingFeesCanBeRefunded() {
    long id = completed();
    long detail = db.count("SELECT id FROM order_detail WHERE order_id=?", id);
    long first =
        aftersale.apply(
            id,
            new PlatformCommands.Aftersale(
                "PARTIAL", "所有商品", List.of(new PlatformCommands.RefundLine(detail, 2)), null));
    act(platform);
    aftersale.decide(first, new PlatformCommands.Decision(0, true, "同意"), false);
    jobs.refund();
    act(user(user));
    long rest = aftersale.apply(id, new PlatformCommands.Aftersale("FULL", "剩余费用", null, null));
    assertEquals(200, db.count("SELECT amount_cents FROM aftersale WHERE id=?", rest));
    act(platform);
    aftersale.decide(rest, new PlatformCommands.Decision(0, true, "同意"), false);
    jobs.refund();
    assertEquals(2600, db.count("SELECT refunded_cents FROM orders WHERE id=?", id));
  }

  @Test
  void cancellationRequestBlocksPickupAndCanBeWithdrawn() {
    long id = paidOrder();
    act(platform);
    fulfillment.action(id, "accept", new PlatformCommands.Action(1, null, null, null));
    act(user(user));
    long request =
        aftersale.apply(id, new PlatformCommands.Aftersale("CANCEL", "暂不需要", null, null));
    act(platform);
    assertThrows(
        BusinessException.class,
        () -> fulfillment.action(id, "ready", new PlatformCommands.Action(2, null, null, null)));
    act(user(user));
    aftersale.withdraw(request, 0);
    act(platform);
    fulfillment.action(id, "ready", new PlatformCommands.Action(2, null, null, null));
    assertEquals(0, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", id));
  }

  @Test
  void zeroAmountCombinedPaymentDoesNotCallGateway() {
    db.update("UPDATE store SET packaging_fee=0 WHERE id=1");
    act(platform);
    long template =
        marketing.coupon(
            1L,
            null,
            new PlatformCommands.Coupon(
                "免商品",
                "REDUCTION",
                0,
                99999,
                null,
                1,
                1,
                LocalDateTime.now(clock).minusHours(1),
                LocalDateTime.now(clock).plusDays(1),
                true));
    act(user(user));
    long coupon = marketing.claim(template);
    add(1, 2, 1);
    var body = oneInput(1);
    var select = body.stores().getFirst();
    var input =
        quoted(
            new PlatformCommands.Checkout(
                List.of(
                    new PlatformCommands.Selection(
                        1, select.cartVersion(), "PICKUP", null, coupon)),
                null,
                null,
                null));
    var c = checkout.submit(UUID.randomUUID().toString(), input);
    assertEquals(0, Db.id(c, "total_cents"));
    gateway.failNextPayments(1);
    checkout.pay(Db.id(c, "id"), 0);
    assertEquals("PAID", checkout.detail(Db.id(c, "id")).get("state"));
  }

  @Test
  void privateEvidenceRequiresOrderOwnershipAndNeverServesPublicPath() throws Exception {
    long id = completed();
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        out);
    String image =
        evidence.upload(
            id,
            new org.springframework.mock.web.MockMultipartFile(
                "file", "test.png", "image/png", out.toByteArray()));
    assertTrue(evidence.read(image).length > 0);
    long other = Long.parseLong(auth.demo(new Commands.DemoLogin(2)).get("id").toString());
    act(user(other));
    assertThrows(BusinessException.class, () -> evidence.read(image));
    act(staff("manager3"));
    assertThrows(BusinessException.class, () -> evidence.read(image));
    act(staff("courier1"));
    assertThrows(BusinessException.class, () -> evidence.read(image));
    mvc.perform(get("/uploads/.private/" + image + ".png")).andExpect(status().isUnauthorized());
  }

  @Test
  void sameCampusDeliveryAddressIsRequiredAndHistoricalOrdersRemainVisibleAfterDisable() {
    long id = completed();
    db.update("UPDATE store SET enabled=false WHERE id=1");
    assertEquals(id, Db.id(fulfillment.detail(id), "id"));
    assertThrows(
        BusinessException.class,
        () -> cart.change(1, new PlatformCommands.CartChange(0, "DISH", 2, Map.of(), 1)));
    db.update("UPDATE store SET enabled=true WHERE id=1");
    long building = db.count("SELECT MIN(id) FROM campus_building WHERE campus_id=1");
    long address =
        db.insert(
            "INSERT INTO address_book(user_id,building_id,room,consignee,phone)"
                + " VALUES(?,?,'101','测试','13800000000')",
            user,
            building);
    add(3, dish3, 1);
    var selection =
        new PlatformCommands.Selection(
            3, Db.id(cart.view(3), "cartVersion"), "DELIVERY", address, null);
    assertThrows(
        BusinessException.class,
        () ->
            checkout.preview(new PlatformCommands.Checkout(List.of(selection), null, null, null)));
  }

  @Test
  void refundAfterSevenDaysRejectedAndExpiredLeasesRecover() {
    long id = completed();
    clock.value.set(clock.instant().plus(Duration.ofDays(8)));
    assertThrows(
        BusinessException.class,
        () -> aftersale.apply(id, new PlatformCommands.Aftersale("FULL", "超过售后时间", null, null)));
    clock.value.set(clock.instant().minus(Duration.ofDays(8)));
    long r = aftersale.apply(id, new PlatformCommands.Aftersale("FULL", "租约恢复", null, null));
    act(platform);
    aftersale.decide(r, new PlatformCommands.Decision(0, true, "同意"), false);
    db.update(
        "UPDATE refund_request SET state='PROCESSING',lease_token='lost-worker',lease_until=? WHERE"
            + " aftersale_id=?",
        LocalDateTime.now(clock).minusSeconds(1),
        r);
    assertTrue(jobs.refund());
    assertEquals(2600, db.count("SELECT refunded_cents FROM orders WHERE id=?", id));
    assertFalse(jobs.refund());
  }

  @Test
  void reportsCountOnePaymentAndRefundByTheirOwnDates() {
    long id = completed();
    act(platform);
    var first = discovery.report(null, null, LocalDate.now(clock), LocalDate.now(clock));
    @SuppressWarnings("unchecked")
    var before = (Map<String, Object>) first.get("metrics");
    assertEquals(1, ((Number) before.get("payment_count")).longValue());
    assertEquals(3900, ((Number) before.get("paid_cents")).longValue());
    act(user(user));
    long detail = db.count("SELECT id FROM order_detail WHERE order_id=?", id);
    long request =
        aftersale.apply(
            id,
            new PlatformCommands.Aftersale(
                "PARTIAL", "一份退款", List.of(new PlatformCommands.RefundLine(detail, 1)), null));
    act(platform);
    aftersale.decide(request, new PlatformCommands.Decision(0, true, "同意"), false);
    clock.value.set(clock.instant().plus(Duration.ofDays(1)));
    jobs.refund();
    @SuppressWarnings("unchecked")
    var after =
        (Map<String, Object>)
            discovery.report(null, null, LocalDate.now(clock), LocalDate.now(clock)).get("metrics");
    assertEquals(0, ((Number) after.get("paid_cents")).longValue());
    assertEquals(1200, ((Number) after.get("refunded_cents")).longValue());
    assertEquals(-1200, ((Number) after.get("net_cents")).longValue());
  }

  @Test
  void newReorderAndReminderPreserveStoreScope() {
    long id = paidOrder();
    fulfillment.reminder(id);
    assertThrows(BusinessException.class, () -> fulfillment.reminder(id));
    cart.reorder(id, Db.id(cart.view(1), "cartVersion"));
    assertEquals(
        2,
        db.count("SELECT SUM(quantity) FROM store_cart_item WHERE user_id=? AND store_id=1", user));
    assertEquals(
        0, db.count("SELECT COUNT(*) FROM store_cart_item WHERE user_id=? AND store_id=3", user));
    cart.clearCurrent(1, Db.id(cart.view(1), "cartVersion"));
    assertEquals(0, db.count("SELECT COUNT(*) FROM store_cart_item WHERE user_id=?", user));
  }

  @Test
  void pendingCancellationAndRefundApprovalCannotDoubleReserve() throws Exception {
    long id = completed();
    long detail = db.count("SELECT id FROM order_detail WHERE order_id=?", id);
    var result =
        race(
            List.of(user(user), user(user)),
            a ->
                aftersale.apply(
                    id,
                    new PlatformCommands.Aftersale(
                        "PARTIAL",
                        "竞争退款",
                        List.of(new PlatformCommands.RefundLine(detail, 2)),
                        null)));
    assertEquals(1, result.successes());
    assertEquals(List.of("AFTERSALE_PENDING"), result.failures());
    assertEquals(2400, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", id));
    assertEquals(2, db.count("SELECT reserved_quantity FROM order_detail WHERE id=?", detail));
  }

  @Test
  void eventTasksAreScopedAndStoreOwnersCannotRetryOtherStores() {
    long id = paidOrder();
    act(staff("manager1"));
    assertFalse(jobs.events(1L).isEmpty());
    assertThrows(BusinessException.class, () -> jobs.events(3L));
    long other = db.count("SELECT MIN(id) FROM outbox_event WHERE store_id=3");
    assertThrows(BusinessException.class, () -> jobs.retryEvent(other));
    assertTrue(
        db.count(
                "SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=? AND store_id=1 AND"
                    + " campus_id=1",
                id)
            > 0);
  }

  @Test
  void cachedCatalogKeepsStringIdsAndDecimalMoneyOnTheWire() throws Exception {
    for (int request = 0; request < 2; request++)
      mvc.perform(get("/api/v1/user/stores/1/products"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].id").isString())
          .andExpect(jsonPath("$.data[0].price").isString())
          .andExpect(jsonPath("$.data[0].categoryId").isString());
  }

  @Test
  void cursorPagesKeepOlderOrdersAndCheckoutsReachableWithinScope() {
    Actor courier = staff("courier1");
    var orders = new ArrayList<Long>();
    var checkouts = new ArrayList<Long>();
    for (int i = 0; i < 105; i++) {
      long c =
          db.insert(
              "INSERT INTO"
                  + " checkout(user_id,request_id,request_hash,state,total_cents,expires_at,created_at,quote_json)"
                  + " VALUES(?,?,?,'CANCELLED',0,?,?, '{}')",
              user,
              UUID.randomUUID().toString(),
              "0".repeat(64),
              LocalDateTime.now(clock),
              LocalDateTime.now(clock));
      checkouts.add(c);
      orders.add(
          db.insert(
              "INSERT INTO"
                  + " orders(number,status,user_id,address_book_id,order_time,amount,store_id,campus_id,checkout_id,courier_id)"
                  + " VALUES(?,6,?,0,?,0,1,1,?,?)",
              "page-" + c,
              user,
              LocalDateTime.now(clock),
              c,
              courier.id()));
    }
    Collections.reverse(orders);
    Collections.reverse(checkouts);
    act(user(user));
    assertCursorPages(checkouts, checkout::list);
    assertCursorPages(orders, cursor -> fulfillment.orders(null, cursor));
    act(staff("manager1"));
    assertCursorPages(orders, cursor -> fulfillment.orders(1L, cursor));
    act(staff("manager3"));
    assertTrue(fulfillment.orders(3L, orders.get(99)).isEmpty());
    assertThrows(BusinessException.class, () -> fulfillment.orders(1L, orders.get(99)));
    act(courier);
    assertCursorPages(orders, cursor -> fulfillment.orders(null, cursor));
    act(staff("courier2"));
    assertTrue(fulfillment.orders(null, orders.get(99)).isEmpty());
    long other = Long.parseLong(auth.demo(new Commands.DemoLogin(2)).get("id").toString());
    act(user(other));
    assertTrue(checkout.list(checkouts.get(99)).isEmpty());
    assertTrue(fulfillment.orders(null, orders.get(99)).isEmpty());
    assertThrows(BusinessException.class, () -> checkout.list(0L));
    assertThrows(BusinessException.class, () -> fulfillment.orders(null, -1L));
  }

  @Test
  void maintenanceBoundsDeletionAndKeepsBusinessAndPendingRecords() {
    var now = LocalDateTime.now(clock);
    for (int i = 0; i < 205; i++) {
      db.update(
          "INSERT INTO auth_session(jti,subject_type,subject_id,auth_version,expires_at)"
              + " VALUES(?,'USER',?,0,?)",
          "old-" + i,
          user,
          now.minusDays(8));
      db.update(
          "INSERT INTO"
              + " outbox_event(event_type,aggregate_id,payload,state,next_attempt_at,created_at)"
              + " VALUES('TEST',0,'{}','SUCCEEDED',?,?)",
          now,
          now.minusDays(31));
    }
    db.update(
        "INSERT INTO outbox_event(event_type,aggregate_id,payload,state,next_attempt_at,created_at)"
            + " VALUES('TEST',0,'{}','FAILED',?,?)",
        now,
        now.minusDays(40));
    long audit = db.count("SELECT COUNT(*) FROM audit_log");
    db.update(
        "INSERT INTO auth_session(jti,subject_type,subject_id,auth_version,expires_at)"
            + " VALUES('boundary','USER',?,0,?)",
        user,
        now.minusDays(7));
    for (String state : List.of("REQUESTED", "PROCESSING"))
      db.update(
          "INSERT INTO"
              + " outbox_event(event_type,aggregate_id,payload,state,next_attempt_at,created_at)"
              + " VALUES('TEST',0,'{}',?,?,?)",
          state,
          now,
          now.minusDays(40));
    maintenance.clean();
    assertEquals(5, db.count("SELECT COUNT(*) FROM auth_session WHERE jti LIKE 'old-%'"));
    assertEquals(5, db.count("SELECT COUNT(*) FROM outbox_event WHERE state='SUCCEEDED'"));
    assertEquals(1, db.count("SELECT COUNT(*) FROM outbox_event WHERE state='FAILED'"));
    assertEquals(
        2, db.count("SELECT COUNT(*) FROM outbox_event WHERE state IN('REQUESTED','PROCESSING')"));
    assertNotNull(db.one("SELECT jti FROM auth_session WHERE jti='boundary'"));
    assertEquals(audit, db.count("SELECT COUNT(*) FROM audit_log"));
    assertNotNull(db.one("SELECT id FROM user WHERE id=?", user));
    assertEquals(1, db.count("SELECT COUNT(*) FROM auth_session WHERE expires_at>?", now));
  }

  @Test
  void concurrentEvidenceCannotExceedPendingLimitAndFailedInsertLeavesNoFile() throws Exception {
    long order = completed();
    for (int i = 0; i < 19; i++) {
      String id = UUID.randomUUID().toString();
      db.update(
          "INSERT INTO evidence(id,user_id,order_id,path,created_at)"
              + " VALUES(?,?,?,?,CURRENT_TIMESTAMP)",
          id,
          user,
          order,
          id + ".png");
    }
    var storage =
        new com.sky.business.ImageStorage(
            evidenceRoot.toString(),
            2,
            1000000,
            0,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    var service =
        new EvidenceService(
            evidenceRoot.toString(), db, new PlatformScope(db), storage, transactionManager);
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        out);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 2; i++)
        futures.add(
            pool.submit(
                () -> {
                  act(user(user));
                  start.await();
                  try {
                    service.upload(
                        order,
                        new org.springframework.mock.web.MockMultipartFile(
                            "file", "x.png", "image/png", out.toByteArray()));
                    return true;
                  } catch (BusinessException ex) {
                    assertEquals("EVIDENCE_LIMIT", ex.code());
                    return false;
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      start.countDown();
      int succeeded = 0;
      for (var future : futures) if (future.get(20, TimeUnit.SECONDS)) succeeded++;
      assertEquals(1, succeeded);
    }
    assertEquals(20, db.count("SELECT COUNT(*) FROM evidence WHERE order_id=?", order));
    var brokenDb = org.mockito.Mockito.mock(Db.class);
    org.mockito.Mockito.when(
            brokenDb.update(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class)))
        .thenThrow(
            new org.springframework.dao.DataIntegrityViolationException("forced insert failure"));
    var failing =
        new EvidenceService(
            evidenceRoot.toString(),
            brokenDb,
            org.mockito.Mockito.mock(PlatformScope.class),
            storage,
            transactionManager);
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            failing.upload(
                order,
                new org.springframework.mock.web.MockMultipartFile(
                    "file", "x.png", "image/png", out.toByteArray())));
    try (var files = java.nio.file.Files.walk(evidenceRoot)) {
      assertEquals(1, files.filter(java.nio.file.Files::isRegularFile).count());
    }
  }

  private void assertCursorPages(
      List<Long> expected, java.util.function.Function<Long, List<Map<String, Object>>> load) {
    var first = load.apply(null);
    assertEquals(expected.subList(0, 100), first.stream().map(r -> Db.id(r, "id")).toList());
    var second = load.apply(Db.id(first.getLast(), "id"));
    assertEquals(expected.subList(100, 105), second.stream().map(r -> Db.id(r, "id")).toList());
    assertTrue(load.apply(Db.id(second.getLast(), "id")).isEmpty());
  }
}
