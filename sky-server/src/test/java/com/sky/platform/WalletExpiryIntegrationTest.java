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

@SpringBootTest(properties = {
    "spring.profiles.active=demo",
    "campus.jwt-secret=wallet-expiry-test-key-at-least-thirty-two-characters",
    "campus.demo-admin-password=TestAdmin!2026",
    "campus.tasks-enabled=false"
})
@Testcontainers
class WalletExpiryIntegrationTest {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
      .withDatabaseName("wallet_expiry_test").withUsername("test").withPassword("test")
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

  static class MutableClock extends Clock {
    final AtomicReference<Instant> at = new AtomicReference<>();
    public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
    public Clock withZone(ZoneId zone) { return this; }
    public Instant instant() { return at.get(); }
  }
  @TestConfiguration
  static class Configuration {
    @Bean @Primary MutableClock walletClock() {
      var clock = new MutableClock();
      clock.at.set(Instant.parse("2026-10-27T04:00:00Z"));
      return clock;
    }
  }

  @Autowired Db db;
  @Autowired CollectionPages pages;
  @Autowired MarketingService marketing;
  @Autowired MutableClock clock;
  @Autowired PlatformTransactionManager manager;
  long user;

  @BeforeEach
  void fixture() {
    clock.at.set(Instant.parse("2026-10-27T04:00:00Z"));
    user = db.insert("INSERT INTO user(openid,name,create_time) VALUES(?,'钱包边界用户',?)",
        UUID.randomUUID().toString(), LocalDateTime.now(clock));
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new Actor(user, "USER", "USER", "", false), null, List.of()));
  }
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }

  long coupon(String state, LocalDateTime end, boolean enabled) {
    long template = db.insert("INSERT INTO coupon(name,kind,threshold_cents,value,total,claimed,per_user,starts_at,ends_at,enabled)"
        + " VALUES(?,'REDUCTION',0,100,1,1,1,?,?,?)", "边界券-" + UUID.randomUUID(),
        LocalDateTime.now(clock).minusDays(1), end, enabled);
    return db.insert("INSERT INTO user_coupon(user_id,coupon_id,state) VALUES(?,?,?)", user, template, state);
  }
  CursorPages.Page<CollectionPages.Wallet> page(String state, String cursor) {
    return pages.wallet(new CursorPages.Request(cursor, 50, state, null));
  }

  @Test
  void effectiveExpiryDoesNotChangeReservationStateOrStoppedClaimSemantics() {
    var at = LocalDateTime.now(clock);
    long expired = coupon("AVAILABLE", at, true);
    long available = coupon("AVAILABLE", at.plusHours(1), true);
    long stopped = coupon("AVAILABLE", at.plusHours(1), false);
    long locked = coupon("LOCKED", at.minusHours(1), true);
    long used = coupon("USED", at.minusHours(1), true);
    var all = page(null, null).records();
    var actual = new HashMap<Long, String>();
    all.forEach(r -> actual.put(Long.valueOf(r.id()), r.state()));
    assertEquals(Map.of(expired, "EXPIRED", available, "AVAILABLE", stopped, "AVAILABLE",
        locked, "LOCKED", used, "USED"), actual);
    assertEquals(List.of(String.valueOf(expired)), page("EXPIRED", null).records().stream().map(CollectionPages.Wallet::id).toList());
    assertEquals(Set.of(String.valueOf(available), String.valueOf(stopped)),
        new HashSet<>(page("AVAILABLE", null).records().stream().map(CollectionPages.Wallet::id).toList()));
    var legacy = marketing.wallet();
    assertEquals("EXPIRED", legacy.stream().filter(r -> Db.id(r, "id") == expired).findFirst().orElseThrow().get("state"));
    assertEquals("AVAILABLE", db.one("SELECT state FROM user_coupon WHERE id=?", expired).get("state"));
    var tx = new TransactionTemplate(manager);
    assertEquals("COUPON_THRESHOLD", assertThrows(BusinessException.class,
        () -> tx.execute(s -> marketing.discount(user, expired, null, 1000))).code());
    assertEquals(100L, tx.execute(s -> marketing.discount(user, stopped, null, 1000)).longValue());
  }

  @Test
  void walletRefreshUsesTheSameExactExpiryBoundaryAsRedemption() {
    var deadline = LocalDateTime.now(clock).plusSeconds(1);
    long owned = coupon("AVAILABLE", deadline, true);
    assertEquals("AVAILABLE", page(null, null).records().getFirst().state());
    clock.at.updateAndGet(v -> v.plusSeconds(1));
    assertTrue(page("AVAILABLE", null).records().isEmpty());
    assertEquals("EXPIRED", page(null, null).records().getFirst().state());
    assertEquals(String.valueOf(owned), page("EXPIRED", null).records().getFirst().id());
    assertEquals("AVAILABLE", db.one("SELECT state FROM user_coupon WHERE id=?", owned).get("state"));
  }

  @Test
  void allExpiredPagesAreCompleteAndDoNotIncludeAnotherUser() {
    var at = LocalDateTime.now(clock);
    for (int i = 0; i < 205; i++) coupon("AVAILABLE", at.minusSeconds(1), true);
    long other = db.insert("INSERT INTO user(openid,name,create_time) VALUES(?,'其他用户',?)",
        UUID.randomUUID().toString(), at);
    long foreign = coupon("AVAILABLE", at.minusSeconds(1), true);
    db.update("UPDATE user_coupon SET user_id=? WHERE id=?", other, foreign);
    var ids = new HashSet<String>();
    String cursor = null;
    do {
      var result = page("EXPIRED", cursor);
      for (var row : result.records()) { assertEquals("EXPIRED", row.state()); assertTrue(ids.add(row.id())); }
      cursor = result.nextCursor();
    } while (cursor != null);
    assertEquals(205, ids.size());
    assertFalse(ids.contains(String.valueOf(foreign)));
    assertEquals(200, marketing.wallet().size());
  }
}
