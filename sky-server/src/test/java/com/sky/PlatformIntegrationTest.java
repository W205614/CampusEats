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
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/platform-openapi.json"), spec);
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

  private void assertCursorPages(
      List<Long> expected, java.util.function.Function<Long, List<Map<String, Object>>> load) {
    var first = load.apply(null);
    assertEquals(expected.subList(0, 100), first.stream().map(r -> Db.id(r, "id")).toList());
    var second = load.apply(Db.id(first.getLast(), "id"));
    assertEquals(expected.subList(100, 105), second.stream().map(r -> Db.id(r, "id")).toList());
    assertTrue(load.apply(Db.id(second.getLast(), "id")).isEmpty());
  }
}
