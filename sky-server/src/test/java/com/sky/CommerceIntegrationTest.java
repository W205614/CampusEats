package com.sky;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sky.api.Commands.*;
import com.sky.business.*;
import com.sky.common.BusinessException;
import com.sky.infra.*;
import com.sky.security.*;
import com.sky.websocket.*;
import java.math.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=test-only-signing-secret-at-least-thirty-two-bytes",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false",
      "spring.datasource.hikari.connection-timeout=15000"
    })
@AutoConfigureMockMvc
@Testcontainers
class CommerceIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("campus_test")
          .withUsername("test")
          .withPassword("test")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));

  @Container
  static final GenericContainer<?> redisContainer =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.data.redis.host", redisContainer::getHost);
    r.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  static class TestClock extends Clock {
    final AtomicReference<Instant> value =
        new AtomicReference<>(Instant.parse("2026-10-07T04:00:00Z"));

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
  @Autowired Json json;
  @Autowired CartService cart;
  @Autowired OrderCommandService commands;
  @Autowired OrderQueryService query;
  @Autowired AddressService addresses;
  @Autowired QuotaService quotas;
  @Autowired TaskRunner tasks;
  @Autowired MockPaymentGateway gateway;
  @Autowired AuthService auth;
  @Autowired ShopService shop;
  @Autowired CatalogService catalog;
  @Autowired TicketService tickets;
  @Autowired UploadService uploads;
  @Autowired StringRedisTemplate redis;
  @Autowired TestClock clock;
  @Autowired MockMvc mvc;
  @Autowired BackgroundJobs jobs;
  @Autowired org.springframework.transaction.PlatformTransactionManager manager;
  Actor admin = new Actor(1, "ADMIN", "ADMIN", "", false);

  @BeforeEach
  void reset() {
    clock.value.set(Instant.parse("2026-10-07T04:00:00Z"));
    gateway.failNextPayments(0);
    gateway.failNextRefunds(0);
    for (String table :
        List.of(
            "order_reservation",
            "payment_attempt",
            "refund_task",
            "outbox_event",
            "audit_log",
            "order_detail",
            "orders",
            "cart_item",
            "cart_state",
            "address_book",
            "auth_session",
            "daily_quota")) db.update("DELETE FROM " + table);
    db.update("DELETE FROM user");
    db.update("DELETE FROM employee WHERE id<>1");
    db.update(
        "UPDATE employee SET auth_version=0,status=1,role='ADMIN',must_change_password=false WHERE"
            + " id=1");
    db.update("UPDATE dish SET price=12,status=1,default_quota=100 WHERE id=2");
    db.update("UPDATE dish SET status=1,default_quota=100 WHERE id IN(1,3)");
    db.update("UPDATE category SET status=1");
    db.update("UPDATE campus_building SET enabled=true");
    db.update(
        "UPDATE shop_settings SET"
            + " open=true,hours='[\"00:00-24:00\"]',delivery_fee=6,packaging_fee=1,version=0 WHERE"
            + " id=1");
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    act(admin);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void act(Actor actor) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + actor.role()))));
  }

  long user(int n) {
    long id = 100 + n;
    db.update(
        "INSERT INTO user(id,openid,name,create_time) VALUES(?,?,?,?)",
        id,
        "test-" + n,
        "用户" + n,
        LocalDateTime.now(clock));
    return id;
  }

  long address(long user) {
    return addresses.save(user, null, new Address(1L, "101", "测试用户", "13800000000"));
  }

  Cart add(long user, long version, String type, long item, Map<String, String> flavor, int qty) {
    return cart.change(user, new CartChange(version, type, item, flavor, qty));
  }

  record Request(long user, String key, Submit input) {}

  Request request(int n) {
    long user = user(n), address = address(user);
    add(user, 0, "DISH", 2, Map.of(), 1);
    Quote quote = commands.preview(user, new Preview(address, 1L));
    return new Request(
        user, UUID.randomUUID().toString(), new Submit(address, 1L, quote.quoteHash(), ""));
  }

  Map<String, Object> submit(Request r) {
    return commands.submit(r.user(), r.key(), r.input());
  }

  long order(Request r) {
    return Db.id(submit(r), "id");
  }

  Map<String, Object> paid(int n) {
    Request r = request(n);
    long id = order(r);
    return commands.pay(r.user(), id, 0);
  }

  void failure(int status, Runnable call) {
    BusinessException ex = assertThrows(BusinessException.class, call::run);
    assertEquals(status, ex.status());
  }

  <T> List<T> parallel(int count, java.util.function.IntFunction<T> work) throws Exception {
    var pool = Executors.newFixedThreadPool(count);
    var start = new CountDownLatch(1);
    try {
      List<Future<T>> fs = new ArrayList<>();
      for (int i = 0; i < count; i++) {
        int n = i;
        fs.add(
            pool.submit(
                () -> {
                  start.await();
                  return work.apply(n);
                }));
      }
      start.countDown();
      var result = new ArrayList<T>();
      for (var f : fs) result.add(f.get(60, TimeUnit.SECONDS));
      return result;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void sameKeyTwentyRequestsCreateOneOrder() throws Exception {
    Request r = request(1);
    var ids = parallel(20, i -> Db.id(submit(r), "id"));
    assertEquals(1, new HashSet<>(ids).size());
    assertEquals(1, db.count("SELECT COUNT(*) FROM orders"));
    assertEquals(1, db.count("SELECT reserved FROM daily_quota WHERE dish_id=2"));
    assertTrue(cart.view(r.user()).items().isEmpty());
    assertEquals(2, cart.view(r.user()).cartVersion());
  }

  @Test
  void reusedKeyDifferentBodyIsConflict() {
    Request r = request(1);
    submit(r);
    failure(
        409,
        () ->
            commands.submit(
                r.user(),
                r.key(),
                new Submit(r.input().addressId(), 1L, r.input().quoteHash(), "不同备注")));
  }

  @Test
  void twoKeysCannotCheckoutOneCartTwice() throws Exception {
    Request r = request(1);
    var result =
        parallel(
            2,
            i -> {
              try {
                commands.submit(r.user(), UUID.randomUUID().toString(), r.input());
                return true;
              } catch (BusinessException ex) {
                assertEquals(409, ex.status());
                return false;
              }
            });
    assertEquals(1, result.stream().filter(Boolean::booleanValue).count());
    assertEquals(1, db.count("SELECT COUNT(*) FROM orders"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void fiftyUsersCompeteForTenPortions(boolean configuredDate) throws Exception {
    if (configuredDate) quotas.configure(LocalDate.now(clock), new Quota(2L, 10, false));
    else db.update("UPDATE dish SET default_quota=10 WHERE id=2");
    List<Request> requests = new ArrayList<>();
    for (int i = 0; i < 50; i++) requests.add(request(i + 1));
    var result =
        parallel(
            50,
            i -> {
              try {
                submit(requests.get(i));
                return true;
              } catch (BusinessException ex) {
                assertEquals("SOLD_OUT", ex.code());
                return false;
              }
            });
    assertEquals(10, result.stream().filter(Boolean::booleanValue).count());
    assertEquals(10, db.count("SELECT COUNT(*) FROM orders"));
    assertEquals(10, db.count("SELECT reserved FROM daily_quota WHERE dish_id=2"));
    assertEquals(40, db.count("SELECT COUNT(*) FROM cart_item"));
  }

  @Test
  void cartVersionPreventsLostUpdate() throws Exception {
    long u = user(1);
    var result =
        parallel(
            2,
            i -> {
              try {
                add(u, 0, "DISH", 2, Map.of(), 1);
                return true;
              } catch (BusinessException ex) {
                assertEquals("CART_CHANGED", ex.code());
                return false;
              }
            });
    assertEquals(1, result.stream().filter(Boolean::booleanValue).count());
    assertEquals(1, db.count("SELECT quantity FROM cart_item WHERE user_id=?", u));
  }

  @Test
  void priceChangeRequiresReconfirmation() {
    Request r = request(1);
    db.update("UPDATE dish SET price=13 WHERE id=2");
    failure(409, () -> submit(r));
    assertEquals(0, db.count("SELECT COUNT(*) FROM orders"));
    assertEquals(1, cart.view(r.user()).items().size());
  }

  @Test
  void snapshotsSurviveAddressDeletion() {
    Request r = request(1);
    long id = order(r);
    addresses.remove(r.user(), r.input().addressId());
    var row = query.detail(new Actor(r.user(), "USER", "USER", "", false), id);
    assertEquals("一号宿舍楼 101", row.get("address"));
    assertEquals("ORIGINAL", row.get("snapshot_source"));
  }

  @Test
  void ownershipProtectsEveryMutation() {
    Request r = request(1);
    long id = order(r);
    long other = user(2);
    failure(404, () -> commands.pay(other, id, 0));
    failure(
        404,
        () ->
            commands.action(
                new Actor(other, "USER", "USER", "", false),
                id,
                "cancel",
                new OrderAction(0L, "", null)));
    failure(404, () -> query.detail(new Actor(other, "USER", "USER", "", false), id));
    failure(404, () -> cart.reorder(other, id, 0));
    failure(404, () -> addresses.makeDefault(other, r.input().addressId()));
  }

  @Test
  void acceptAndCancelOnlyOneWins() throws Exception {
    var row = paid(1);
    long id = Db.id(row, "id"), u = Db.id(row, "user_id");
    var results =
        parallel(
            2,
            i -> {
              try {
                commands.action(
                    i == 0 ? admin : new Actor(u, "USER", "USER", "", false),
                    id,
                    i == 0 ? "accept" : "cancel",
                    new OrderAction(1L, "", null));
                return true;
              } catch (BusinessException ex) {
                assertEquals(409, ex.status());
                return false;
              }
            });
    assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
    assertEquals(0, db.count("SELECT reserved FROM daily_quota WHERE dish_id=2"));
    int state = Db.integer(db.one("SELECT status FROM orders WHERE id=?", id), "status");
    assertEquals(state == 3 ? 1 : 0, db.count("SELECT consumed FROM daily_quota WHERE dish_id=2"));
  }

  @Test
  void repeatedCancelReleasesOnce() {
    Request r = request(1);
    long id = order(r);
    Actor a = new Actor(r.user(), "USER", "USER", "", false);
    commands.action(a, id, "cancel", new OrderAction(0L, "", null));
    commands.action(a, id, "cancel", new OrderAction(0L, "", null));
    assertEquals(0, db.count("SELECT reserved FROM daily_quota WHERE dish_id=2"));
  }

  @Test
  void afterAcceptanceCancelDoesNotReleaseConsumed() {
    var row = paid(1);
    long id = Db.id(row, "id");
    commands.action(admin, id, "accept", new OrderAction(1L, "", null));
    commands.action(admin, id, "cancel", new OrderAction(2L, "", null));
    assertEquals(1, db.count("SELECT consumed FROM daily_quota WHERE dish_id=2"));
    assertEquals(1, db.count("SELECT pay_status FROM orders WHERE id=?", id));
    assertTrue(tasks.runRefund());
    assertEquals(2, db.count("SELECT pay_status FROM orders WHERE id=?", id));
    assertEquals(
        1,
        db.count(
            "SELECT COUNT(*) FROM outbox_event WHERE event_type='ORDER_REFUNDED' AND"
                + " aggregate_id=?",
            id));
  }

  @Test
  void midnightCancellationReleasesOriginalDate() {
    clock.value.set(Instant.parse("2026-10-07T15:55:00Z"));
    Request r = request(1);
    long id = order(r);
    clock.value.set(clock.instant().plusSeconds(3600));
    commands.action(
        new Actor(r.user(), "USER", "USER", "", false),
        id,
        "cancel",
        new OrderAction(0L, "", null));
    assertEquals(
        0,
        db.count(
            "SELECT reserved FROM daily_quota WHERE business_date='2026-10-07' AND dish_id=2"));
    assertEquals(0, db.count("SELECT COUNT(*) FROM daily_quota WHERE business_date='2026-10-08'"));
  }

  @Test
  void setmealReservationsAreAtomic() {
    long u = user(1), a = address(u);
    add(u, 0, "SETMEAL", 1, Map.of(), 2);
    var q = commands.preview(u, new Preview(a, 1L));
    quotas.configure(LocalDate.now(clock), new Quota(3L, 1, false));
    failure(
        409,
        () ->
            commands.submit(u, UUID.randomUUID().toString(), new Submit(a, 1L, q.quoteHash(), "")));
    assertEquals(0, db.count("SELECT COUNT(*) FROM orders"));
    assertEquals(0, db.count("SELECT COALESCE(SUM(reserved),0) FROM daily_quota"));
    assertEquals(1, cart.view(u).items().size());
  }

  @Test
  void expiryReturnsQuotaAndCannotBePaid() {
    Request r = request(1);
    long id = order(r);
    clock.value.set(clock.instant().plusSeconds(901));
    failure(409, () -> commands.pay(r.user(), id, 0));
    commands.expire(id);
    assertEquals(6, db.count("SELECT status FROM orders WHERE id=?", id));
    assertEquals(0, db.count("SELECT reserved FROM daily_quota WHERE dish_id=2"));
  }

  @Test
  void quotaCannotDropBelowUsage() {
    Request r = request(1);
    submit(r);
    failure(409, () -> quotas.configure(LocalDate.now(clock), new Quota(2L, 0, false)));
  }

  @Test
  void refundFailureRetryAndLeaseRecovery() {
    var row = paid(1);
    long id = Db.id(row, "id");
    commands.action(admin, id, "cancel", new OrderAction(1L, "", null));
    gateway.failNextRefunds(1);
    tasks.runRefund();
    assertEquals(1, db.count("SELECT pay_status FROM orders WHERE id=?", id));
    assertEquals(
        "REQUESTED", db.one("SELECT state FROM refund_task WHERE order_id=?", id).get("state"));
    db.update(
        "UPDATE refund_task SET state='PROCESSING',lease_until=?,lease_token='interrupted' WHERE"
            + " order_id=?",
        LocalDateTime.now(clock).minusSeconds(1),
        id);
    tasks.runRefund();
    assertEquals(2, db.count("SELECT pay_status FROM orders WHERE id=?", id));
  }

  @Test
  void refundFiveFailuresNeedManualRetry() {
    var row = paid(1);
    long id = Db.id(row, "id");
    commands.action(admin, id, "cancel", new OrderAction(1L, "", null));
    gateway.failNextRefunds(5);
    for (int i = 0; i < 5; i++) {
      db.update("UPDATE refund_task SET next_attempt_at=?", LocalDateTime.now(clock));
      tasks.runRefund();
    }
    var task = db.one("SELECT * FROM refund_task WHERE order_id=?", id);
    assertEquals("FAILED", task.get("state"));
    assertEquals(5, Db.integer(task, "attempts"));
    tasks.retry("refund", Db.id(task, "id"));
    tasks.runRefund();
    assertEquals(2, db.count("SELECT pay_status FROM orders WHERE id=?", id));
  }

  @Test
  void mockPaymentFailureRollsBackAndRetryIsIdempotent() {
    Request r = request(1);
    long id = order(r);
    gateway.failNextPayments(1);
    failure(503, () -> commands.pay(r.user(), id, 0));
    assertEquals(0, db.count("SELECT pay_status FROM orders WHERE id=?", id));
    commands.pay(r.user(), id, 0);
    commands.pay(r.user(), id, 0);
    assertEquals(1, db.count("SELECT COUNT(*) FROM payment_attempt WHERE order_id=?", id));
  }

  @Test
  void logoutAndPasswordChangeRevokeImmediately() {
    var login = auth.login(new Login("admin", "TestAdmin!2026"));
    String token = login.get("token").toString();
    Actor actor = auth.authenticate(token);
    auth.logout(actor);
    failure(401, () -> auth.authenticate(token));
  }

  @Test
  void legacyMd5LoginMigratesAndForcesPasswordChange() {
    db.update(
        "INSERT INTO employee(id,name,username,password,phone,sex,id_number,role)"
            + " VALUES(2,'旧员工','legacy',?,'','','','OPERATOR')",
        com.sky.common.Hashes.digest("MD5", "LegacyPass!2026"));
    var login = auth.login(new Login("legacy", "LegacyPass!2026"));
    assertTrue(login.get("mustChangePassword").equals(true));
    assertTrue(
        db.one("SELECT password FROM employee WHERE id=2")
            .get("password")
            .toString()
            .startsWith("$argon2"));
    var a = auth.authenticate(login.get("token").toString());
    auth.change(a, new Password("LegacyPass!2026", "NewPassword!2026"));
    failure(401, () -> auth.authenticate(login.get("token").toString()));
  }

  @Test
  void websocketTicketsAreSingleUseAndRevocable() {
    var login = auth.login(new Login("admin", "TestAdmin!2026"));
    Actor a = auth.authenticate(login.get("token").toString());
    act(a);
    String ticket = tickets.issue().get("ticket").toString();
    assertNotNull(tickets.consume(ticket));
    assertNull(tickets.consume(ticket));
    String next = tickets.issue().get("ticket").toString();
    auth.logout(a);
    assertNull(tickets.consume(next));
  }

  @Test
  void courierCanOnlyOperateAssignedOrder() {
    var row = paid(1);
    long id = Db.id(row, "id");
    commands.action(admin, id, "accept", new OrderAction(1L, "", null));
    db.update(
        "INSERT INTO"
            + " employee(id,name,username,password,phone,sex,id_number,role,must_change_password)"
            + " VALUES(2,'配送员','courier','unused','','','','DELIVERER',false)");
    commands.action(admin, id, "assign", new OrderAction(2L, "", 2L));
    failure(
        403,
        () ->
            commands.action(
                new Actor(3, "ADMIN", "DELIVERER", "", false),
                id,
                "dispatch",
                new OrderAction(3L, "", null)));
    commands.action(
        new Actor(2, "ADMIN", "DELIVERER", "", false),
        id,
        "dispatch",
        new OrderAction(3L, "", null));
    assertEquals(4, db.count("SELECT status FROM orders WHERE id=?", id));
  }

  @Test
  void shopBuildingAndFlavorsAreChecked() {
    long u = user(1), a = address(u);
    failure(409, () -> add(u, 0, "DISH", 1, Map.of("辣度", "不支持"), 1));
    add(u, 0, "DISH", 2, Map.of(), 1);
    db.update("UPDATE campus_building SET enabled=false WHERE id=1");
    failure(409, () -> commands.preview(u, new Preview(a, 1L)));
    db.update("UPDATE campus_building SET enabled=true WHERE id=1");
    db.update("UPDATE shop_settings SET open=false");
    failure(409, () -> commands.preview(u, new Preview(a, 1L)));
  }

  @Test
  void imageExtensionCannotBypassDecoder() {
    failure(
        400,
        () ->
            uploads.upload(
                new MockMultipartFile(
                    "file", "test.png", "image/png", "<script>alert(1)</script>".getBytes())));
  }

  @Test
  void httpContractsRejectUnauthorizedAndWrongRoles() throws Exception {
    SecurityContextHolder.clearContext();
    mvc.perform(get("/api/v1/admin/orders"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    var login = auth.demo(new DemoLogin(1));
    mvc.perform(
            get("/api/v1/admin/employees").header("Authorization", "Bearer " + login.get("token")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/user/cart/items")
                .header("Authorization", "Bearer " + login.get("token"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/user/menu/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.data[0].id").isString());
  }

  @Test
  void exportedWorkbookIsBinaryAndSpecMatchesStringIds() throws Exception {
    SecurityContextHolder.clearContext();
    String token = auth.login(new Login("admin", "TestAdmin!2026")).get("token").toString();
    var response =
        mvc.perform(
                get("/api/v1/admin/reports/export?begin=2026-10-01&end=2026-10-07")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    try (var workbook =
        new org.apache.poi.xssf.usermodel.XSSFWorkbook(
            new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
      assertEquals(8, workbook.getSheetAt(0).getPhysicalNumberOfRows());
    }
    mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.schemas.OrderView.properties.id.type").value("string"))
        .andExpect(
            jsonPath("$.components.schemas.ProductView.properties.price.type").value("string"));
  }

  @Test
  void defaultAddressIsUniqueAndReorderRepricesCurrentMenu() {
    Request r = request(1);
    long id = order(r);
    long second = addresses.save(r.user(), null, new Address(2L, "202", "测试", "13900000000"));
    addresses.makeDefault(r.user(), second);
    assertEquals(
        1, addresses.list(r.user()).stream().filter(x -> Db.bool(x, "is_default")).count());
    db.update("UPDATE dish SET price=15 WHERE id=2");
    cart.reorder(r.user(), id, 2);
    assertEquals(new BigDecimal("15.00"), cart.view(r.user()).items().getFirst().price());
    db.update("UPDATE dish SET status=0 WHERE id=2");
    var result = cart.reorder(r.user(), id, 3);
    assertFalse(((List<?>) result.get("skipped")).isEmpty());
  }

  @Test
  void expiredOutboxLeaseCanRecoverAndOverdueDeliveryDoesNotComplete() {
    Request r = request(1);
    long id = order(r);
    db.update(
        "UPDATE outbox_event SET state='PROCESSING',lease_until=?,lease_token='interrupted'",
        LocalDateTime.now(clock).minusSeconds(1));
    assertTrue(tasks.runEvent());
    assertEquals(1, db.count("SELECT COUNT(*) FROM outbox_event WHERE state='SUCCEEDED'"));
    commands.pay(r.user(), id, 0);
    commands.action(admin, id, "accept", new OrderAction(1L, "", null));
    db.update(
        "INSERT INTO"
            + " employee(id,name,username,password,phone,sex,id_number,role,must_change_password)"
            + " VALUES(2,'配送员','courier','unused','','','','DELIVERER',false)");
    commands.action(admin, id, "assign", new OrderAction(2L, "", 2L));
    commands.action(admin, id, "dispatch", new OrderAction(3L, "", null));
    clock.value.set(clock.instant().plusSeconds(3601));
    jobs.scanTimeouts();
    assertEquals(4, db.count("SELECT status FROM orders WHERE id=?", id));
    assertTrue(
        Db.bool(db.one("SELECT delivery_overdue FROM orders WHERE id=?", id), "delivery_overdue"));
  }

  @Test
  void redisOutageDoesNotPreventDurableOrderOrCacheRetry() {
    String container = redisContainer.getContainerId();
    redisContainer.getDockerClient().pauseContainerCmd(container).exec();
    try {
      Request r = request(1);
      long id = order(r);
      commands.pay(r.user(), id, 0);
      commands.action(admin, id, "cancel", new OrderAction(1L, "", null));
      assertTrue(tasks.runRefund());
      assertEquals(2, db.count("SELECT pay_status FROM orders WHERE id=?", id));
      assertFalse(catalog.menu(1, "DISH").isEmpty());
      failure(503, () -> tickets.issue());
    } finally {
      redisContainer.getDockerClient().unpauseContainerCmd(container).exec();
    }
  }

  @Test
  void limitsAndDisabledAccountsRejectWrites() throws Exception {
    String token = auth.demo(new DemoLogin(1)).get("token").toString();
    SecurityContextHolder.clearContext();
    mvc.perform(get("/api/v1/user/orders?size=101").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    String employeeToken = auth.login(new Login("admin", "TestAdmin!2026")).get("token").toString();
    db.update("UPDATE employee SET status=0 WHERE id=1");
    failure(401, () -> auth.authenticate(employeeToken));
  }

  @Test
  void cacheInvalidationSurvivesRedisFailureAndTransactionRollback() {
    String container = redisContainer.getContainerId();
    redisContainer.getDockerClient().pauseContainerCmd(container).exec();
    try {
      catalog.category(1L, new Category("校园热餐", 1, 1, true));
      assertEquals(
          2,
          db.count(
              "SELECT COUNT(*) FROM outbox_event WHERE event_type='CACHE_INVALIDATE' AND"
                  + " state='REQUESTED'"));
    } finally {
      redisContainer.getDockerClient().unpauseContainerCmd(container).exec();
    }
    assertTrue(tasks.runEvent());
    assertTrue(tasks.runEvent());
    assertEquals(
        2,
        db.count(
            "SELECT COUNT(*) FROM outbox_event WHERE event_type='CACHE_INVALIDATE' AND"
                + " state='SUCCEEDED'"));
    new org.springframework.transaction.support.TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              catalog.category(1L, new Category("回滚分类", 1, 1, true));
              status.setRollbackOnly();
            });
    assertEquals(
        2, db.count("SELECT COUNT(*) FROM outbox_event WHERE event_type='CACHE_INVALIDATE'"));
    assertEquals("校园热餐", db.one("SELECT name FROM category WHERE id=1").get("name"));
  }

  @Test
  void httpRoundTripAcceptsStringNumbersAndPreservesLargeIds() throws Exception {
    var session = auth.demo(new DemoLogin(1));
    long user = Long.parseLong(session.get("id").toString());
    String token = session.get("token").toString();
    db.update(
        "INSERT INTO address_book(id,user_id,building_id,room,consignee,phone)"
            + " VALUES(9007199254740993,?,1,'101','测试','13800000000')",
        user);
    SecurityContextHolder.clearContext();
    mvc.perform(get("/api/v1/user/addresses").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("9007199254740993"));
    mvc.perform(
            post("/api/v1/user/cart/items")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(
                    "{\"cartVersion\":\"0\",\"itemType\":\"DISH\",\"itemId\":\"2\",\"flavors\":{},\"delta\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cartVersion").value("1"))
        .andExpect(jsonPath("$.data.items[0].itemId").value("2"));
  }

  @Test
  void categoryDeletionWaitsForProductCreationAndRechecksReferences() throws Exception {
    long category = catalog.category(null, new Category("并发分类", 1, 0, true));
    var inserted = new CountDownLatch(1);
    var commitCreation = new CountDownLatch(1);
    var deletingStarted = new CountDownLatch(1);
    var deletingConnection = new AtomicLong();
    var pool = Executors.newFixedThreadPool(2);
    var tx = new org.springframework.transaction.support.TransactionTemplate(manager);
    try {
      var creating = pool.submit(() -> {
        act(admin);
        try {
          return tx.execute(status -> {
            long product = catalog.save("DISH", null, new Product(
                "并发餐品", category, new BigDecimal("12.00"), true, "", "", List.of(), List.of()));
            inserted.countDown();
            try {
              assertTrue(commitCreation.await(15, TimeUnit.SECONDS));
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(ex);
            }
            return product;
          });
        } finally {
          SecurityContextHolder.clearContext();
        }
      });
      assertTrue(inserted.await(10, TimeUnit.SECONDS));
      var deleting = pool.submit(() -> {
        act(admin);
        try {
          tx.executeWithoutResult(status -> {
            deletingConnection.set(db.count("SELECT CONNECTION_ID()"));
            deletingStarted.countDown();
            catalog.deleteCategory(category);
          });
          return "DELETED";
        } catch (BusinessException ex) {
          return ex.code();
        } finally {
          SecurityContextHolder.clearContext();
        }
      });
      assertTrue(deletingStarted.await(10, TimeUnit.SECONDS));
      awaitMysqlLockWait(deletingConnection.get());
      commitCreation.countDown();
      long product = creating.get(15, TimeUnit.SECONDS);
      assertEquals("CATEGORY_IN_USE", deleting.get(15, TimeUnit.SECONDS));
      assertEquals(category, db.count("SELECT category_id FROM dish WHERE id=?", product));
      assertEquals(1, db.count("SELECT COUNT(*) FROM category WHERE id=?", category));
      assertEquals(0, db.count(
          "SELECT COUNT(*) FROM dish d LEFT JOIN category c ON c.id=d.category_id WHERE c.id IS NULL"));
      assertEquals(0, db.count(
          "SELECT COUNT(*) FROM audit_log WHERE action='CATEGORY_DELETE' AND target_id=?", category));
    } finally {
      commitCreation.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
      db.update("DELETE FROM dish WHERE category_id=?", category);
      db.update("DELETE FROM category WHERE id=?", category);
    }
  }

  private void awaitMysqlLockWait(long connectionId) throws Exception {
    // Observe a real InnoDB wait before committing the competing transaction; no timing guess.
    try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
        var statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.threads t"
                + " ON t.THREAD_ID=w.REQUESTING_THREAD_ID WHERE t.PROCESSLIST_ID=?")) {
      statement.setLong(1, connectionId);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < deadline) {
        try (var rows = statement.executeQuery()) {
          rows.next();
          if (rows.getLong(1) > 0) return;
        }
        TimeUnit.MILLISECONDS.sleep(20);
      }
      fail("Competing category deletion never entered an InnoDB lock wait");
    }
  }

  @Test
  void categoryDeletionAuditsOnlySuccessfulChanges() {
    long category = catalog.category(null, new Category("删除审计分类", 1, 0, true));
    catalog.deleteCategory(category);
    assertEquals(0, db.count("SELECT COUNT(*) FROM category WHERE id=?", category));
    assertEquals(1, db.count(
        "SELECT COUNT(*) FROM audit_log WHERE action='CATEGORY_DELETE' AND target_id=?", category));
    failure(404, () -> catalog.deleteCategory(category));
    assertEquals(1, db.count(
        "SELECT COUNT(*) FROM audit_log WHERE action='CATEGORY_DELETE' AND target_id=?", category));
  }
}
