package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.api.Commands.*;
import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.security.Actor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
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

/** Real MySQL locking and ordering checks for the bounded whole-menu/discovery paths only. */
@SpringBootTest(properties = {
    "spring.profiles.active=demo",
    "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
    "campus.demo-admin-password=TestAdmin!2026",
    "campus.tasks-enabled=false"
})
@Testcontainers
class CatalogBoundsIntegrationTest {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
      .withDatabaseName("catalog_bounds").withUsername("test").withPassword("test")
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
  @Autowired StoreCatalog catalog;
  @Autowired DiscoveryPages pages;
  long campus, store, dishes, setmeals;
  final Actor admin = new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false);

  void act() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(admin, null, List.of()));
  }

  @BeforeEach
  void fixture() {
    act();
    campus = db.insert("INSERT INTO campus(name) VALUES(?)", "bounds-" + UUID.randomUUID());
    store = db.insert("INSERT INTO store(campus_id,name,open,hours) VALUES(?,?,true,'[\"00:00-24:00\"]')",
        campus, "目录边界店");
    dishes = db.insert("INSERT INTO category(store_id,name,type,sort,status) VALUES(?,'菜品',1,0,1)", store);
    setmeals = db.insert("INSERT INTO category(store_id,name,type,sort,status) VALUES(?,'套餐',2,0,1)", store);
  }

  private void seed(String table, long category, int count, IntFunction<String> name) {
    var batch = new ArrayList<Object[]>();
    for (int i = 0; i < count; i++) batch.add(new Object[] {store, category, name.apply(i), i % 2});
    db.jdbc.batchUpdate("INSERT INTO " + table
        + "(store_id,category_id,name,price,status) VALUES(?,?,?,10,?)", batch);
  }

  private Product dish(String name) {
    return new Product(name, dishes, BigDecimal.TEN, true, "", null, List.of(), List.of());
  }

  private long size() {
    return db.count("SELECT (SELECT COUNT(*) FROM dish WHERE store_id=?)"
        + "+(SELECT COUNT(*) FROM setmeal WHERE store_id=?)", store, store);
  }

  @AfterEach
  void clearActor() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void concurrentDishAndSetmealCreationShareTheLastSlotIncludingDisabledItems() throws Exception {
    seed("dish", dishes, 498, i -> "原菜品" + i);
    seed("setmeal", setmeals, 1, i -> "原套餐");
    long component = db.count("SELECT MIN(id) FROM dish WHERE store_id=?", store);
    // The existing component must be available when the newly created set meal is enabled.
    db.update("UPDATE dish SET status=1 WHERE id=?", component);
    var meal = new Product("并发套餐", setmeals, BigDecimal.TEN, true, "", null,
        List.of(), List.of(new Component(component, 1)));
    var start = new CountDownLatch(1);
    try (var threads = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<String>>();
      for (String type : List.of("DISH", "SETMEAL")) futures.add(threads.submit(() -> {
        act();
        try {
          assertTrue(start.await(5, TimeUnit.SECONDS));
          catalog.save(store, type, null, type.equals("DISH") ? dish("并发菜品") : meal);
          return "created";
        } catch (BusinessException ex) {
          return ex.code();
        } finally {
          SecurityContextHolder.clearContext();
        }
      }));
      start.countDown();
      var outcomes = new ArrayList<String>();
      for (var future : futures) outcomes.add(future.get(15, TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "created"));
      assertEquals(1, Collections.frequency(outcomes, "CATALOG_LIMIT"));
      assertEquals(500, size());
    }
  }

  @Test
  void exactLimitLoadsWholeMenuButHistoricalOverflowIsPreservedAndStillEditable() {
    seed("dish", dishes, 500, i -> "菜品" + i);
    assertEquals(500, catalog.products(store, true, null, null).size());
    long id = db.count("SELECT MIN(id) FROM dish WHERE store_id=?", store);
    seed("setmeal", setmeals, 1, i -> "导入历史套餐");
    assertEquals("CATALOG_LIMIT", assertThrows(BusinessException.class,
        () -> catalog.products(store, false, null, null)).code());
    assertEquals("CATALOG_LIMIT", assertThrows(BusinessException.class,
        () -> catalog.save(store, "DISH", null, dish("不可新增"))).code());
    catalog.save(store, "DISH", id, dish("原商品仍可维护"));
    assertEquals("原商品仍可维护", db.one("SELECT name FROM dish WHERE id=?", id).get("name"));
    assertEquals(501, size());
  }

  @Test
  void boundedBranchesKeepCompleteTypedPagingAndSparseCaseSensitiveLiteralMatches() {
    seed("dish", dishes, 250, i -> (i < 240 ? "Needle%_Exact-" : "needle%_Exact-") + i);
    seed("setmeal", setmeals, 250, i -> (i < 240 ? "Needle%_Exact-" : "needle%_Exact-") + i);
    db.update("UPDATE dish SET status=1 WHERE store_id=?", store);
    db.update("UPDATE setmeal SET status=1 WHERE store_id=?", store);
    var expected = new ArrayList<String>();
    for (String type : List.of("DISH", "SETMEAL")) {
      String table = type.equals("DISH") ? "dish" : "setmeal";
      for (var row : db.rows("SELECT id FROM " + table
          + " WHERE store_id=? AND LOCATE(BINARY 'Needle%_Exact',BINARY name)>0 ORDER BY id", store))
        expected.add(type + ":" + Db.id(row, "id"));
    }
    var actual = new ArrayList<String>();
    String cursor = null;
    do {
      var page = pages.search(campus, "Needle%_", null, true, cursor, 31);
      assertTrue(page.records().size() <= 31);
      actual.addAll(page.records().stream().map(r -> r.get("item_type") + ":" + Db.id(r, "id")).toList());
      assertEquals(page.hasMore(), page.nextCursor() != null);
      cursor = page.nextCursor();
    } while (cursor != null);
    assertEquals(480, actual.size());
    assertEquals(expected, actual);
    assertEquals(20, pages.search(campus, "needle%_", null, true, null, 200).records().size());
    assertTrue(pages.search(campus, "Needle%X", null, true, null, 200).records().isEmpty());
    assertEquals(1, pages.search(campus, "Needle%_", dishes, true, null, 1).records().size());
    assertTrue(pages.search(campus, "Needle%_", null, false, null, 200).records().isEmpty());
  }
}
