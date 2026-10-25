package com.sky;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

import com.sky.api.Commands;
import com.sky.business.MockPaymentGateway;
import com.sky.business.PaymentGateway;
import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.platform.*;
import com.sky.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** Controlled interleavings at the payment, dispatch and refund recovery boundaries. */
@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false"
    })
@Testcontainers
class PlatformConcurrencyTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("platform_races")
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
    final AtomicReference<Instant> value =
        new AtomicReference<>(Instant.parse("2026-10-15T04:00:00Z"));

    public ZoneId getZone() {
      return ZoneId.of("Asia/Shanghai");
    }

    public Clock withZone(ZoneId zone) {
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
    TestClock raceClock() {
      return new TestClock();
    }
  }

  @Autowired Db db;
  @Autowired AuthService auth;
  @Autowired StoreCart carts;
  @Autowired CheckoutService checkouts;
  @Autowired FulfillmentService fulfillment;
  @Autowired AftersaleService aftersales;
  @Autowired PlatformJobs jobs;
  @Autowired TestClock clock;
  @MockitoSpyBean MockPaymentGateway gateway;
  Actor user;
  final Actor admin = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false);

  void act(Actor actor) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  @BeforeEach
  void reset() {
    clock.value.set(Instant.parse("2026-10-15T04:00:00Z"));
    for (String table :
        List.of(
            "mock_refund_receipt",
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
    db.update("UPDATE store SET enabled=true,open=true,hours='[\"00:00-24:00\"]'");
    db.update("UPDATE campus SET enabled=true");
    db.update("UPDATE dish SET status=1,default_quota=100");
    db.update("UPDATE category SET status=1");
    long id = Long.parseLong(auth.demo(new Commands.DemoLogin(1)).get("id").toString());
    user = new Actor(id, "USER", "USER", "", false);
    gateway.failNextPayments(0);
    gateway.failNextRefunds(0);
    act(user);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  long submit() {
    var selections = new ArrayList<PlatformCommands.Selection>();
    for (long store : List.of(1L, 3L)) {
      long dish = store == 1 ? 2 : db.count("SELECT MIN(id) FROM dish WHERE store_id=3");
      var cart = carts.view(store);
      cart =
          carts.change(
              store,
              new PlatformCommands.CartChange(
                  Db.id(cart, "cartVersion"), "DISH", dish, Map.of(), 2));
      selections.add(
          new PlatformCommands.Selection(store, Db.id(cart, "cartVersion"), "PICKUP", null, null));
    }
    var input = new PlatformCommands.Checkout(selections, null, null, null);
    var quote = checkouts.preview(input);
    return Db.id(
        checkouts.submit(
            UUID.randomUUID().toString(),
            new PlatformCommands.Checkout(selections, null, quote.quoteHash(), null)),
        "id");
  }

  <T> Future<T> run(ExecutorService pool, Actor actor, Callable<T> task) {
    return pool.submit(
        () -> {
          act(actor);
          try {
            return task.call();
          } finally {
            clear();
          }
        });
  }

  @Test
  void paymentAlreadyInGatewayWinsAgainstTimeoutWithoutPartialChildren() throws Exception {
    long id = submit();
    var enteredGateway = new CountDownLatch(1);
    var finishGateway = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              enteredGateway.countDown();
              assertTrue(finishGateway.await(10, TimeUnit.SECONDS));
              return invocation.callRealMethod();
            })
        .when(gateway)
        .pay(org.mockito.ArgumentMatchers.any(PaymentGateway.Payment.class));
    try (var pool = Executors.newFixedThreadPool(2)) {
      Future<?> payment = run(pool, user, () -> checkouts.pay(id, 0));
      try {
        assertTrue(enteredGateway.await(10, TimeUnit.SECONDS));
        clock.value.set(clock.instant().plus(Duration.ofMinutes(16)));
        var expiryStarted = new CountDownLatch(1);
        Future<?> expiry =
            run(
                pool,
                user,
                () -> {
                  expiryStarted.countDown();
                  checkouts.expire(id);
                  return null;
                });
        assertTrue(expiryStarted.await(10, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> expiry.get(1, TimeUnit.SECONDS));
        finishGateway.countDown();
        payment.get(20, TimeUnit.SECONDS);
        expiry.get(20, TimeUnit.SECONDS);
      } finally {
        finishGateway.countDown();
      }
    }
    assertEquals("PAID", checkouts.detail(id).get("state"));
    assertEquals(
        2,
        db.count(
            "SELECT COUNT(*) FROM orders WHERE checkout_id=? AND status=2 AND pay_status=1", id));
    assertEquals(2, db.count("SELECT COUNT(*) FROM payment_attempt"));
    assertEquals(4, db.count("SELECT SUM(reserved) FROM daily_quota"));
  }

  @Test
  void timeoutWinnerCannotBePaidAndReleasesAllChildren() {
    long id = submit();
    clock.value.set(clock.instant().plus(Duration.ofMinutes(15)));
    checkouts.expire(id);
    assertThrows(BusinessException.class, () -> checkouts.pay(id, 0));
    assertEquals("CANCELLED", checkouts.detail(id).get("state"));
    assertEquals(
        2,
        db.count(
            "SELECT COUNT(*) FROM orders WHERE checkout_id=? AND status=6 AND pay_status=0", id));
    assertEquals(0, db.count("SELECT COUNT(*) FROM payment_attempt"));
    assertEquals(0, db.count("SELECT COALESCE(SUM(reserved),0) FROM daily_quota"));
  }

  @Test
  void courierClaimAndManualAssignmentHaveExactlyOneWinner() throws Exception {
    long checkout = submit();
    checkouts.pay(checkout, 0);
    long order = db.count("SELECT id FROM orders WHERE checkout_id=? AND store_id=1", checkout);
    db.update("UPDATE orders SET fulfillment='DELIVERY' WHERE id=?", order);
    act(admin);
    fulfillment.action(order, "accept", new PlatformCommands.Action(1, null, null, null));
    fulfillment.action(order, "ready", new PlatformCommands.Action(2, null, null, null));
    fulfillment.action(order, "publish", new PlatformCommands.Action(3, null, null, null));
    long task = db.count("SELECT id FROM delivery_task WHERE order_id=?", order);
    long assigned = db.count("SELECT id FROM employee WHERE username='courier1b'");
    long claimant = db.count("SELECT id FROM employee WHERE username='courier1'");
    var courier = new Actor(claimant, "ADMIN", "DELIVERER", "", false, null, 1L);
    var start = new CyclicBarrier(2);
    try (var pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> claim =
          run(
              pool,
              courier,
              () -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                  fulfillment.claim(task, 0);
                  return true;
                } catch (BusinessException ex) {
                  assertEquals("DELIVERY_CLAIMED", ex.code());
                  return false;
                }
              });
      Future<Boolean> assign =
          run(
              pool,
              admin,
              () -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                  fulfillment.action(
                      order, "assign", new PlatformCommands.Action(4, null, assigned, null));
                  return true;
                } catch (BusinessException ex) {
                  assertTrue(Set.of("VERSION_CONFLICT", "DELIVERY_CLAIMED").contains(ex.code()));
                  return false;
                }
              });
      boolean claimed = claim.get(20, TimeUnit.SECONDS);
      boolean manuallyAssigned = assign.get(20, TimeUnit.SECONDS);
      assertNotEquals(claimed, manuallyAssigned);
      long winner = claimed ? claimant : assigned;
      assertEquals(winner, db.count("SELECT courier_id FROM delivery_task WHERE id=?", task));
      assertEquals(winner, db.count("SELECT courier_id FROM orders WHERE id=?", order));
    }
  }

  @Test
  void gatewaySuccessBeforeWorkerLossIsAppliedOnceWhenLeaseExpires() {
    long checkout = submit();
    checkouts.pay(checkout, 0);
    long order = db.count("SELECT id FROM orders WHERE checkout_id=? AND store_id=1", checkout);
    act(admin);
    fulfillment.action(order, "accept", new PlatformCommands.Action(1, null, null, null));
    fulfillment.action(order, "ready", new PlatformCommands.Action(2, null, null, null));
    String code =
        db.one("SELECT pickup_code FROM orders WHERE id=?", order).get("pickup_code").toString();
    fulfillment.verify(order, new PlatformCommands.Action(3, null, null, code));
    act(user);
    long aftersale =
        aftersales.apply(order, new PlatformCommands.Aftersale("FULL", "恢复验收", null, null));
    act(admin);
    aftersales.decide(aftersale, new PlatformCommands.Decision(0, true, "同意"), false);
    long request = db.count("SELECT id FROM refund_request WHERE aftersale_id=?", aftersale);
    long amount = db.count("SELECT amount_cents FROM refund_request WHERE id=?", request);
    gateway.refund(
        new PaymentGateway.Refund(
            "refund-request:" + request, "checkout:" + checkout, amount, "CNY"));
    db.update(
        "UPDATE refund_request SET state='PROCESSING',lease_token='worker-lost',lease_until=? WHERE"
            + " id=?",
        LocalDateTime.now(clock).minusSeconds(1),
        request);
    gateway.failNextRefunds(1);
    assertTrue(jobs.refund());
    assertFalse(jobs.refund());
    assertEquals(
        "SUCCEEDED", db.one("SELECT state FROM refund_request WHERE id=?", request).get("state"));
    assertEquals(amount, db.count("SELECT refunded_cents FROM orders WHERE id=?", order));
    assertEquals(0, db.count("SELECT reserved_refund_cents FROM orders WHERE id=?", order));
    assertEquals(2, db.count("SELECT refunded_quantity FROM order_detail WHERE order_id=?", order));
    assertThrows(
        BusinessException.class,
        () ->
            gateway.refund(
                new PaymentGateway.Refund(
                    "refund-request:" + (request + 1000000), "checkout:" + checkout, 1, "CNY")));
  }
}
