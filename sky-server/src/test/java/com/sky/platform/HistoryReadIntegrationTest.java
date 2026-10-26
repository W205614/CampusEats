package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.security.Actor;
import com.sky.web.ApiAdvice;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** Database-backed pagination, public projection, and store isolation for changed history reads. */
@SpringBootTest(properties = {
    "spring.profiles.active=demo",
    "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
    "campus.demo-admin-password=TestAdmin!2026", "campus.tasks-enabled=false"
})
@Testcontainers
class HistoryReadIntegrationTest {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
      .withDatabaseName("history_reads").withUsername("test").withPassword("test")
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

  @Autowired Db db;
  @Autowired DiscoveryService discovery;
  @Autowired AuditHistory audit;
  long campus, store, otherStore;
  final Actor platform = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false);
  final LocalDate day = LocalDate.of(2026, 10, 26);

  void act(Actor actor) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(actor, null, List.of()));
  }

  @BeforeEach
  void fixture() {
    act(platform);
    campus = db.insert("INSERT INTO campus(name) VALUES(?)", "history-" + UUID.randomUUID());
    store = db.insert("INSERT INTO store(campus_id,name) VALUES(?,'历史店铺')", campus);
    otherStore = db.insert("INSERT INTO store(campus_id,name) VALUES(?,'其他店铺')", campus);
  }

  @AfterEach
  void clearActor() { SecurityContextHolder.clearContext(); }

  @Test
  void reviewsPagePastOneHundredAndPublicProjectionHidesInternalFields() {
    var values = new ArrayList<Object[]>();
    // review order IDs are globally unique; there are no order fixtures or writes to daily data.
    long base = store * 1000;
    for (int i = 0; i < 205; i++) values.add(new Object[] {base + i, store, "评价" + i, i == 5});
    db.jdbc.batchUpdate("INSERT INTO review(order_id,user_id,store_id,rating,content,hidden,created_at)"
        + " VALUES(?,1,?,5,?,?, '2026-10-26 12:00:00')", values);
    var first = discovery.merchantReviews(store, new ReviewQuery(null, null));
    assertEquals(100, first.records().size());
    var second = discovery.merchantReviews(store,
        new ReviewQuery(first.records().getLast().id(), 100));
    var third = discovery.merchantReviews(store,
        new ReviewQuery(second.records().getLast().id(), 100));
    assertEquals(100, second.records().size());
    assertEquals(5, third.records().size());
    var ids = new HashSet<Long>();
    for (var page : List.of(first, second, third))
      for (var row : page.records()) assertTrue(ids.add(row.id()));
    assertEquals(205, ids.size());
    assertEquals(204, first.stats().total());
    SecurityContextHolder.clearContext();
    var publicPage = discovery.publicReviews(store, new ReviewQuery(null, 1000));
    assertEquals(204, publicPage.records().size());
    var wire = (Map<?, ?>) ApiAdvice.wire(publicPage.records().getFirst());
    assertEquals(Set.of("id", "rating", "content", "reply", "createdAt"), wire.keySet());
    assertTrue(discovery.publicReviews(otherStore, new ReviewQuery(null, 1000)).records().isEmpty());
    db.update("UPDATE store SET enabled=false WHERE id=?", store);
    assertEquals("STORE_DISABLED", assertThrows(BusinessException.class,
        () -> discovery.publicReviews(store, new ReviewQuery(null, null))).code());
    db.update("UPDATE store SET enabled=true WHERE id=?", store);
    db.update("UPDATE campus SET enabled=false WHERE id=?", campus);
    assertEquals("STORE_DISABLED", assertThrows(BusinessException.class,
        () -> discovery.publicReviews(store, new ReviewQuery(null, null))).code());
  }

  @Test
  void auditKeepsDefaultTwoHundredAndCombinesFiltersWithStoreAuthorization() {
    var values = new ArrayList<Object[]>();
    for (int i = 0; i < 205; i++) values.add(new Object[] {11L, "STORE_SAVE", "记录" + i,
        day.atStartOfDay(), store, campus});
    values.add(new Object[] {12L, "STORE_SAVE", "其他操作人", day.atStartOfDay(), store, campus});
    values.add(new Object[] {11L, "STAFF_SAVE", "其他操作", day.atStartOfDay(), store, campus});
    values.add(new Object[] {11L, "STORE_SAVE", "日期外", day.plusDays(1).atStartOfDay(), store, campus});
    values.add(new Object[] {11L, "STORE_SAVE", "其他店铺", day.atStartOfDay(), otherStore, campus});
    db.jdbc.batchUpdate("INSERT INTO audit_log(actor_type,actor_id,action,target_id,detail,created_at,store_id,campus_id)"
        + " VALUES('ADMIN',?,?,0,?,?,?,?)", values);
    var query = new AuditQuery(null, null, 11L, "STORE_SAVE", day, day);
    var first = audit.list(store, query);
    assertEquals(200, first.size());
    var second = audit.list(store, new AuditQuery(first.getLast().id(), null, 11L, "STORE_SAVE", day, day));
    assertEquals(5, second.size());
    assertTrue(second.getFirst().id() < first.getLast().id());
    assertTrue(first.stream().allMatch(row -> row.actorId() == 11 && row.storeId() == store));
    act(new Actor(99, "ADMIN", "SHOP_MANAGER", "", false, store, campus));
    assertEquals(205, audit.list(store, new AuditQuery(null, 1000, 11L, "STORE_SAVE", day, day)).size());
    assertEquals("FORBIDDEN", assertThrows(BusinessException.class, () -> audit.list(otherStore, query)).code());
    assertEquals("FORBIDDEN", assertThrows(BusinessException.class, () -> audit.list(null, query)).code());
    assertEquals("FORBIDDEN", assertThrows(BusinessException.class,
        () -> discovery.merchantReviews(otherStore, new ReviewQuery(null, null))).code());
  }

  @Test
  void invalidHistoryParametersAreRejectedBeforeTheQuery() {
    for (Integer limit : List.of(0, -1, 1001)) {
      assertThrows(BusinessException.class, () -> discovery.publicReviews(store, new ReviewQuery(null, limit)));
      assertThrows(BusinessException.class, () -> audit.list(null, new AuditQuery(null, limit, null, null, null, null)));
    }
    for (AuditQuery query : List.of(
        new AuditQuery(0L, null, null, null, null, null),
        new AuditQuery(null, null, -1L, null, null, null),
        new AuditQuery(null, null, null, "x' OR 1=1", null, null),
        new AuditQuery(null, null, null, null, day, null),
        new AuditQuery(null, null, null, null, day, day.minusDays(1)),
        new AuditQuery(null, null, null, null, day, day.plusDays(366))))
      assertThrows(BusinessException.class, () -> audit.list(null, query));
    assertThrows(BusinessException.class, () -> discovery.publicReviews(store, new ReviewQuery(-1L, 10)));
  }
}
