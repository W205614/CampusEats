package com.sky.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.platform.ReportQueryService;
import com.sky.security.Actor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** Isolated source/ledger invariants, closed-period snapshots and report read consistency. */
@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false"
    })
@Testcontainers
class FinanceIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("finance_test")
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

  static class TestClock extends Clock {
    final AtomicReference<Instant> value =
        new AtomicReference<>(Instant.parse("2026-10-28T04:00:00Z"));

    public ZoneId getZone() {
      return ZoneId.of("Asia/Shanghai");
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return value.get();
    }

    void day(int day) {
      value.set(LocalDate.of(2026, 10, day).atTime(12, 0).atZone(getZone()).toInstant());
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    TestClock financeClock() {
      return new TestClock();
    }
  }

  @MockitoSpyBean Db db;
  @Autowired FinanceService finance;
  @Autowired LedgerService ledger;
  @Autowired ReportQueryService reports;
  @Autowired TestClock clock;
  @Autowired PlatformTransactionManager manager;
  long campus, store, other;

  void act(Actor actor) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  @BeforeEach
  void fixture() {
    clock.day(28);
    act(new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false));
    campus = db.insert("INSERT INTO campus(name) VALUES(?)", "finance-" + UUID.randomUUID());
    store = db.insert("INSERT INTO store(campus_id,name) VALUES(?,'账务店铺')", campus);
    other = db.insert("INSERT INTO store(campus_id,name) VALUES(?,'其他店铺')", campus);
    db.update("UPDATE finance_cutover SET captured_at='2026-10-26 12:00:00' WHERE id=1");
  }

  @AfterEach
  void clear() {
    reset(db);
    SecurityContextHolder.clearContext();
  }

  long order(long amount) {
    var at = LocalDateTime.now(clock);
    long checkout =
        db.insert(
            "INSERT INTO"
                + " checkout(user_id,request_id,request_hash,state,total_cents,expires_at,created_at,paid_at,quote_json)"
                + " VALUES(1,?,?,'PAID',?,?,?,?,'{}')",
            UUID.randomUUID().toString(),
            "0".repeat(64),
            amount,
            at.plusMinutes(15),
            at,
            at);
    return db.insert(
        "INSERT INTO"
            + " orders(number,status,user_id,address_book_id,order_time,amount,store_id,campus_id,checkout_id,paid_cents,pay_status,completed_at)"
            + " VALUES(?,5,1,0,?,?,?,?,?,?,1,?)",
        UUID.randomUUID().toString(),
        at,
        BigDecimal.valueOf(amount, 2),
        store,
        campus,
        checkout,
        amount,
        at);
  }

  void payment(long id) {
    new TransactionTemplate(manager).executeWithoutResult(s -> ledger.payment(id));
  }

  long refund(long order, long amount, String state) {
    var at = LocalDateTime.now(clock);
    long id =
        db.insert(
            "INSERT INTO"
                + " refund_request(order_id,store_id,amount_cents,state,next_attempt_at,created_at)"
                + " VALUES(?,?,?,?,?,?)",
            order,
            store,
            amount,
            state,
            at,
            at);
    if (state.equals("SUCCEEDED"))
      new TransactionTemplate(manager)
          .executeWithoutResult(
              s -> {
                db.update(
                    "UPDATE orders SET refunded_cents=refunded_cents+? WHERE id=?", amount, order);
                db.update("UPDATE refund_request SET succeeded_at=? WHERE id=?", at, id);
                ledger.refund(id);
              });
    return id;
  }

  @Test
  void sourceIsIdempotentAndLedgerSharesBusinessRollback() {
    long id = order(1000);
    payment(id);
    payment(id);
    assertEquals(1, db.count("SELECT COUNT(*) FROM finance_ledger WHERE order_id=?", id));
    db.update("UPDATE orders SET paid_cents=1200 WHERE id=?", id);
    assertEquals(
        "LEDGER_CONFLICT", assertThrows(BusinessException.class, () -> payment(id)).code());
    assertEquals(1000, db.count("SELECT amount_cents FROM finance_ledger WHERE order_id=?", id));
    long rolledBack = order(300);
    assertThrows(
        IllegalStateException.class,
        () ->
            new TransactionTemplate(manager)
                .executeWithoutResult(
                    s -> {
                      ledger.payment(rolledBack);
                      throw new IllegalStateException("rollback fixture");
                    }));
    assertEquals(0, db.count("SELECT COUNT(*) FROM finance_ledger WHERE order_id=?", rolledBack));
    assertThrows(
        org.springframework.transaction.IllegalTransactionStateException.class,
        () -> ledger.payment(id));
  }

  @Test
  void unsuccessfulRefundNeverPostsAndDuplicateSuccessPostsOnlyOnce() {
    long id = order(1000);
    payment(id);
    long pending = refund(id, 400, "REQUESTED");
    assertEquals(
        "LEDGER_SOURCE",
        assertThrows(
                BusinessException.class,
                () ->
                    new TransactionTemplate(manager)
                        .executeWithoutResult(s -> ledger.refund(pending)))
            .code());
    long succeeded = refund(id, 300, "SUCCEEDED");
    new TransactionTemplate(manager).executeWithoutResult(s -> ledger.refund(succeeded));
    assertEquals(2, db.count("SELECT COUNT(*) FROM finance_ledger WHERE order_id=?", id));
    assertEquals(700, finance.preview(store, null).netCents());
    assertEquals(
        0,
        finance
            .reconcile(store, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28))
            .differenceCount());
  }

  @Test
  void openingBalanceIsSeparateAndDifferencesAreReportedWithoutRepair() {
    long id = order(1000);
    db.update("UPDATE orders SET refunded_cents=200 WHERE id=?", id);
    db.update(
        "INSERT INTO finance_opening(order_id,store_id,paid_cents,refunded_cents,captured_at)"
            + " VALUES(?,?,1000,200,'2026-10-26 12:00:00')",
        id,
        store);
    refund(id, 50, "SUCCEEDED");
    var preview = finance.preview(store, null);
    assertEquals(800, preview.openingNetCents());
    assertEquals(-50, preview.netCents());
    var good = finance.reconcile(store, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28));
    assertEquals(0, good.differenceCount());
    assertEquals(750, good.ledgerNetCents());
    db.update("UPDATE orders SET paid_cents=1100 WHERE id=?", id);
    var bad = finance.reconcile(store, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28));
    assertEquals(1, bad.differenceCount());
    assertEquals(id, bad.differences().getFirst().orderId());
    assertEquals(1100, db.count("SELECT paid_cents FROM orders WHERE id=?", id));
  }

  @Test
  void closedDayFreezesSourcesAndLaterRefundMakesNegativeNextDay() {
    clock.day(27);
    long id = order(1000);
    payment(id);
    clock.day(28);
    var first = finance.create(store, LocalDate.of(2026, 10, 27));
    assertEquals(1000, first.netCents());
    assertEquals(1, first.entryCount());
    refund(id, 600, "SUCCEEDED");
    assertEquals(first.id(), finance.create(store, LocalDate.of(2026, 10, 27)).id());
    assertEquals(1000, finance.detail(first.id(), store).netCents());
    assertEquals(1, finance.settlementEntries(first.id(), store, null, 50).records().size());
    assertEquals(
        "SETTLEMENT_NOT_CLOSED",
        assertThrows(
                BusinessException.class, () -> finance.create(store, LocalDate.of(2026, 10, 28)))
            .code());
    clock.day(29);
    var second = finance.create(store, LocalDate.of(2026, 10, 28));
    assertEquals(-600, second.netCents());
    assertTrue(second.carryForward());
    assertEquals(
        400,
        finance
            .reconcile(store, LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28))
            .ledgerNetCents());
    assertEquals(
        "BEFORE_FINANCE_START",
        assertThrows(
                BusinessException.class, () -> finance.create(store, LocalDate.of(2026, 10, 25)))
            .code());
  }

  @Test
  void signedPagesCoverPastTwoHundredAndRejectScopeChanges() {
    var at = LocalDateTime.now(clock);
    var values = new ArrayList<Object[]>();
    for (int i = 0; i < 205; i++)
      values.add(new Object[] {store * 10000 + i, store * 10000 + i, store, at});
    db.jdbc.batchUpdate(
        "INSERT INTO"
            + " finance_ledger(source_type,source_id,order_id,store_id,amount_cents,occurred_at)"
            + " VALUES('PAYMENT',?,?,?,1,?)",
        values);
    var first = finance.ledger(store, null, null, null, null, 200);
    var last = finance.ledger(store, null, null, null, first.nextCursor(), 200);
    assertEquals(200, first.records().size());
    assertEquals(5, last.records().size());
    assertFalse(last.hasMore());
    assertEquals(
        "INVALID_CURSOR",
        assertThrows(
                BusinessException.class,
                () -> finance.ledger(other, null, null, null, first.nextCursor(), 200))
            .code());
    act(new Actor(90, "ADMIN", "SHOP_MANAGER", "", false, store, campus));
    assertEquals(205, first.records().size() + last.records().size());
    assertEquals(
        "FORBIDDEN",
        assertThrows(
                BusinessException.class, () -> finance.ledger(other, null, null, null, null, 50))
            .code());
    assertEquals(
        "FORBIDDEN",
        assertThrows(
                BusinessException.class,
                () ->
                    finance.reconcile(
                        store, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28)))
            .code());
    assertThrows(BusinessException.class, () -> finance.ledger(store, null, null, null, null, 201));
  }

  @Test
  void reportsUseOneSnapshotWhenPaymentChangesBetweenSummaryAndDaily() throws Exception {
    long id = order(1000);
    AtomicBoolean changed = new AtomicBoolean();
    doAnswer(
            invocation -> {
              Object result = invocation.callRealMethod();
              String sql = invocation.getArgument(0);
              if (sql.startsWith("SELECT COUNT(DISTINCT o.checkout_id)")
                  && changed.compareAndSet(false, true))
                CompletableFuture.runAsync(
                        () ->
                            db.update("UPDATE orders SET paid_cents=2000,amount=20 WHERE id=?", id))
                    .get();
              return result;
            })
        .when(db)
        .one(anyString(), any(Object[].class));
    var result = reports.view(null, store, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28));
    assertTrue(changed.get());
    assertEquals(1000, result.metrics().paidCents());
    assertEquals(
        1000, result.daily().stream().mapToLong(ReportQueryService.Daily::paidCents).sum());
    assertEquals(
        1000, result.stores().stream().mapToLong(ReportQueryService.StoreTotal::paidCents).sum());
    assertEquals(2000, db.count("SELECT paid_cents FROM orders WHERE id=?", id));
  }

  @Test
  void settlementWaitsForAnAdmittedPaymentWhichCommitsAcrossMidnight() throws Exception {
    clock.day(27);
    long id = order(1000);
    var posted = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var freezing = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              String sql = invocation.getArgument(0);
              if (sql.equals("SELECT id FROM store WHERE id=? FOR UPDATE")) freezing.countDown();
              return invocation.callRealMethod();
            })
        .when(db)
        .one(anyString(), any(Object[].class));
    var posting =
        CompletableFuture.runAsync(
            () ->
                new TransactionTemplate(manager)
                    .executeWithoutResult(
                        s -> {
                          ledger.payment(id);
                          posted.countDown();
                          try {
                            if (!release.await(2, TimeUnit.SECONDS))
                              throw new IllegalStateException("release timeout");
                          } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                          }
                        }));
    assertTrue(posted.await(2, TimeUnit.SECONDS));
    clock.day(28);
    var freezingResult =
        CompletableFuture.supplyAsync(
            () -> {
              act(new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false));
              try {
                return finance.create(store, LocalDate.of(2026, 10, 27));
              } finally {
                SecurityContextHolder.clearContext();
              }
            });
    try {
      assertTrue(freezing.await(1, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> freezingResult.get(150, TimeUnit.MILLISECONDS));
    } finally {
      release.countDown();
    }
    posting.get(3, TimeUnit.SECONDS);
    var saved = freezingResult.get(3, TimeUnit.SECONDS);
    assertEquals(1000, saved.netCents());
    assertEquals(1, saved.entryCount());
  }
}
