package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.business.OrderCommandService;
import com.sky.business.PaymentGateway;
import com.sky.finance.LedgerService;
import com.sky.infra.Db;
import com.sky.infra.RuntimeOperations;
import com.sky.security.Actor;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
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

/** The configured stage deadline, legacy compatibility timer and operational SQL agree. */
@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=delivery-deadline-test-signing-key-at-least-thirty-two",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false"
    })
@Testcontainers
class DeliveryDeadlineIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("delivery_deadline_test")
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

  static class DeadlineClock extends Clock {
    final AtomicReference<Instant> value =
        new AtomicReference<>(Instant.parse("2026-10-27T04:00:00Z"));

    public ZoneId getZone() {
      return ZoneId.of("Asia/Shanghai");
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return value.get();
    }

    void advance(Duration duration) {
      value.updateAndGet(i -> i.plus(duration));
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    DeadlineClock deadlineClock() {
      return new DeadlineClock();
    }
  }

  @Autowired Db db;
  @Autowired DeadlineClock clock;
  @Autowired OperationsService service;
  @Autowired FulfillmentStages stages;
  @Autowired OrderCommandService orders;
  @Autowired CheckoutService checkouts;
  @Autowired PlatformScope scope;
  @Autowired PaymentGateway payments;
  @Autowired LedgerService ledger;
  @Autowired PlatformTransactionManager manager;
  long store, order;
  OperationsJobs stageJobs;
  PlatformJobs legacyJobs;

  @BeforeEach
  void fixture() {
    clock.value.set(Instant.parse("2026-10-27T04:00:00Z"));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false), null, List.of()));
    long campus = db.insert("INSERT INTO campus(name) VALUES(?)", "deadline-" + UUID.randomUUID());
    store = db.insert("INSERT INTO store(campus_id,name) VALUES(?,'时限测试店')", campus);
    long user =
        db.insert(
            "INSERT INTO user(openid,name,create_time) VALUES(?,'时限测试用户',?)",
            UUID.randomUUID().toString(),
            at());
    long courier =
        db.insert(
            "INSERT INTO employee(name,username,password,role,campus_id,status,must_change_password,phone,sex,id_number)"
                + " VALUES('测试配送员',?,'test-only','DELIVERER',?,1,false,'','','')",
            "deadline-" + UUID.randomUUID().toString().substring(0, 12),
            campus);
    long checkout =
        db.insert(
            "INSERT INTO checkout(user_id,request_id,request_hash,state,total_cents,expires_at,created_at,paid_at,quote_json)"
                + " VALUES(?,?,?,'PAID',1000,?,?,?,'{}')",
            user,
            UUID.randomUUID().toString(),
            "0".repeat(64),
            at().plusMinutes(15),
            at(),
            at());
    order =
        db.insert(
            "INSERT INTO orders(number,status,user_id,address_book_id,order_time,amount,store_id,campus_id,checkout_id,paid_cents,pay_status,fulfillment,ready,delivery_started_at,courier_id)"
                + " VALUES(?,4,?,0,?,10,?,?,?,1000,1,'DELIVERY',true,?,?)",
            "DEADLINE-" + UUID.randomUUID(),
            user,
            at(),
            store,
            campus,
            checkout,
            at(),
            courier);
    db.insert(
        "INSERT INTO delivery_task(order_id,campus_id,courier_id,state) VALUES(?,?,?,'PICKED')",
        order,
        campus,
        courier);
    stageJobs = new OperationsJobs(db, clock, service, new RuntimeOperations(clock, true), true);
    legacyJobs =
        new PlatformJobs(
            db,
            clock,
            payments,
            checkouts,
            scope,
            manager,
            true,
            new RuntimeOperations(clock, true),
            orders,
            ledger);
  }

  @AfterEach
  void clear() {
    // Keep independent tests from scheduling one another's active fixtures.
    db.update("UPDATE orders SET status=5 WHERE store_id=?", store);
    SecurityContextHolder.clearContext();
  }

  private LocalDateTime at() {
    return LocalDateTime.now(clock);
  }

  private void start(int minutes) {
    service.policy(store, new OperationsService.Policy(10, 30, 15, 15, minutes, 120, 0));
    new TransactionTemplate(manager).executeWithoutResult(s -> stages.sync(order));
  }

  private boolean flagged() {
    return Db.bool(db.one("SELECT delivery_overdue FROM orders WHERE id=?", order), "delivery_overdue");
  }

  private long version() {
    return db.count("SELECT version FROM orders WHERE id=?", order);
  }

  private long exceptions() {
    return db.count(
        "SELECT COUNT(*) FROM order_exception WHERE order_id=? AND kind='STAGE_OVERDUE'", order);
  }

  /** Execute the actual operational query against MySQL, replacing wall time with the fixed Clock. */
  private long operationalCount(int fromEnd) throws Exception {
    var script = Files.readString(Path.of("..", "scripts", "ops-check.ps1"));
    var sql = Pattern.compile("(?s)\\$sql=@'\\R(.*?)\\R'@").matcher(script);
    assertTrue(sql.find(), "Cannot find the operational SQL block");
    var statements =
        Arrays.stream(sql.group(1).split(";"))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .toList();
    String query = statements.get(statements.size() - fromEnd).replace("NOW()", "?");
    var args = new ArrayList<Object>();
    for (long i = 0; i < query.chars().filter(c -> c == '?').count(); i++) args.add(at());
    args.add(store);
    return db.count(query + " AND o.store_id=?", args.toArray());
  }

  @Test
  void longDeadlineSurvivesOldTimerPolicyChangesAndCorrectsPrematureFlag() throws Exception {
    start(120);
    var deadline = at().plusMinutes(120);
    clock.advance(Duration.ofMinutes(61));
    legacyJobs.markOverdue();
    orders.markOverdue(order); // The compatibility write must independently reject platform orders.
    assertFalse(flagged());
    assertEquals(0, version());
    assertEquals(0, operationalCount(2));

    db.update("UPDATE orders SET delivery_overdue=true WHERE id=?", order);
    stageJobs.work();
    assertFalse(flagged(), "An old premature flag must be corrected before the frozen deadline");
    assertEquals(0, version());
    assertEquals(0, exceptions());
    service.policy(store, new OperationsService.Policy(10, 30, 15, 15, 30, 120, 1));
    assertEquals(
        deadline, Db.time(db.one("SELECT deadline_at FROM order_stage WHERE order_id=?", order).get("deadline_at")));
    assertEquals(0, operationalCount(2));

    clock.advance(Duration.ofMinutes(59));
    stageJobs.work();
    stageJobs.work();
    stageJobs.work();
    assertTrue(flagged());
    assertEquals(1, exceptions());
    assertEquals(1, operationalCount(2));
    assertEquals(0, version(), "Derived timeout flags must not invalidate fulfillment commands");
    assertEquals(0, db.count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=? AND event_type='ORDER_OVERDUE'", order));
    assertEquals(1, db.count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=? AND event_type='ORDER_EXCEPTION'", order));
  }

  @Test
  void shortDeadlineAndOperationalCheckBecomeOverdueAtThirtyMinutes() throws Exception {
    start(30);
    clock.advance(Duration.ofMinutes(29));
    stageJobs.work();
    assertFalse(flagged());
    assertEquals(0, operationalCount(2));
    clock.advance(Duration.ofMinutes(1));
    stageJobs.work();
    assertTrue(flagged());
    assertEquals(1, exceptions());
    assertEquals(1, operationalCount(2));
    assertEquals(0, version());
  }

  @Test
  void migratedDeadlineDoesNotReuseHistoricalPickupTime() throws Exception {
    var baseline = at();
    var pickup = baseline.minusHours(5);
    db.update("UPDATE orders SET delivery_started_at=?,delivery_overdue=true WHERE id=?", pickup, order);
    db.update(
        "INSERT INTO order_stage(order_id,stage,stage_seq,started_at,deadline_at,migrated) VALUES(?,'DELIVERING',0,?,?,true)",
        order,
        baseline,
        baseline.plusMinutes(60));
    stageJobs.work();
    assertFalse(flagged());
    assertEquals(0, operationalCount(2));
    clock.advance(Duration.ofMinutes(60));
    stageJobs.work(); // Tail wrap keeps the bounded sweep fair.
    stageJobs.work();
    assertTrue(flagged());
    assertEquals(1, operationalCount(2));
    var exception = db.one("SELECT migrated,deadline_at FROM order_exception WHERE order_id=?", order);
    assertTrue(Db.bool(exception, "migrated"));
    assertEquals(baseline.plusMinutes(60), Db.time(exception.get("deadline_at")));
    assertEquals(pickup, Db.time(db.one("SELECT delivery_started_at FROM orders WHERE id=?", order).get("delivery_started_at")));
    assertEquals(0, version());
  }

  @Test
  void trueLegacyOrdersRetainSixtyMinuteTimerAndIdempotence() throws Exception {
    db.update("UPDATE orders SET checkout_id=NULL WHERE id=?", order);
    clock.advance(Duration.ofMinutes(59));
    legacyJobs.markOverdue();
    assertFalse(flagged());
    assertEquals(0, operationalCount(2));
    clock.advance(Duration.ofMinutes(1).plusSeconds(1));
    legacyJobs.markOverdue();
    legacyJobs.markOverdue();
    orders.markOverdue(order);
    assertTrue(flagged());
    assertEquals(1, version());
    assertEquals(1, operationalCount(2));
    assertEquals(0, operationalCount(1));
    assertEquals(1, db.count("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=? AND event_type='ORDER_OVERDUE'", order));
  }

  @Test
  void missingPlatformStageIsAnIntegrityIssueAndNeverFallsBackToOldTimer() throws Exception {
    db.update("UPDATE orders SET delivery_started_at=? WHERE id=?", at().minusHours(5), order);
    legacyJobs.markOverdue();
    stageJobs.work();
    assertFalse(flagged());
    assertEquals(0, operationalCount(2));
    assertEquals(1, operationalCount(1));
    assertEquals(0, version());
  }

  @Test
  void aCandidateCompletedOrCancelledBeforeItsLockKeepsHistoricalTimeoutFlag() {
    start(30);
    for (int status : List.of(5, 6)) {
      db.update("UPDATE orders SET status=?,delivery_overdue=true WHERE id=?", status, order);
      service.detect(order);
      assertTrue(flagged(), "A delayed scan must not rewrite the terminal timeout history");
      assertEquals(0, version());
      assertEquals("FINISHED", db.one("SELECT stage FROM order_stage WHERE order_id=?", order).get("stage"));
    }
  }
}
