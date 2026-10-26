package com.sky.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sky.infra.Db;
import com.sky.security.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {"spring.profiles.active=demo",
    "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
    "campus.demo-admin-password=TestAdmin!2026", "campus.tasks-enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class CartReadIntegrationTest {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
      .withDatabaseName("cart_read_boundary").withUsername("test").withPassword("test")
      .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));
  @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
  @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }
  @MockitoSpyBean Db db;
  @Autowired StoreCart carts;
  @Autowired AuthService auth;
  @Autowired FulfillmentService fulfillment;
  @Autowired DiscoveryPages discovery;
  @Autowired PlatformJobs jobs;
  @Autowired MockMvc mvc;
  long user, campus, store, dish, meal;
  @BeforeEach void fixture() {
    user = db.insert("INSERT INTO user(openid,name) VALUES(?,'购物车验收')", UUID.randomUUID().toString());
    campus = db.insert("INSERT INTO campus(name) VALUES(?)", "cart-" + UUID.randomUUID());
    store = db.insert("INSERT INTO store(campus_id,name) VALUES(?,'有商品的店')", campus);
    long category = db.insert("INSERT INTO category(store_id,name,type,status) VALUES(?,'菜',1,1)", store);
    dish = db.insert("INSERT INTO dish(store_id,category_id,name,price,status,image) VALUES(?,?,'菜品',12,0,'/demo-images/dish.svg')", store, category);
    meal = db.insert("INSERT INTO setmeal(store_id,category_id,name,price,status,image) VALUES(?,?,'套餐',20,1,'/demo-images/dish.svg')", store, category);
    act();
  }
  void act() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        new Actor(user, "USER", "USER", "", false), null, List.of()));
  }
  @AfterEach void clear() { SecurityContextHolder.clearContext(); }
  void item(String type, long id, String flavor) {
    db.update("INSERT INTO store_cart_item(user_id,store_id,item_type,item_id,quantity,flavors,flavor_hash) VALUES(?,?,?,?,2,?,?)",
        user, store, type, id, flavor, com.sky.common.Hashes.sha256(flavor));
  }
  @Test void hundredsOfHistoricalEmptyStoresDoNotAddQueriesOrResetVersions() {
    carts.view(store);
    for (int i = 0; i < 205; i++) {
      long empty = db.insert("INSERT INTO store(campus_id,name) VALUES(?,?)", campus, "空店" + i);
      carts.view(empty);
    }
    item("DISH", dish, "{\"辣度\":\"微辣\"}");
    item("SETMEAL", meal, "{}");
    clearInvocations(db);
    var result = carts.all();
    assertEquals(1, result.size());
    @SuppressWarnings("unchecked") var items = (List<Map<String, Object>>) result.getFirst().get("items");
    assertEquals(2, items.size());
    assertEquals("菜品", items.getFirst().get("name"));
    assertEquals(0, ((Number) items.getFirst().get("status")).intValue());
    assertEquals(Map.of("辣度", "微辣"), items.getFirst().get("flavors"));
    assertEquals(2, mockingDetails(db).getInvocations().stream().filter(i -> i.getMethod().getName().equals("rows")).count());
    carts.clearCurrent(store, 0);
    assertTrue(carts.all().isEmpty());
    assertEquals(1, Db.id(carts.view(store), "cartVersion"));
    assertEquals(206, db.count("SELECT COUNT(*) FROM store_cart_state WHERE user_id=?", user));
    var conflict = assertThrows(com.sky.common.BusinessException.class, () -> carts.change(store,
        new PlatformCommands.CartChange(0, "DISH", dish, Map.of(), -1)));
    assertEquals("CART_CHANGED", conflict.code());
  }
  @Test void batchReadStillRejectsMissingProductsAndDoesNotMixOtherUsers() {
    carts.view(store);
    item("DISH", dish, "{}");
    long other = db.insert("INSERT INTO user(openid) VALUES(?)", UUID.randomUUID().toString());
    db.update("INSERT INTO store_cart_state(user_id,store_id) VALUES(?,?)", other, store);
    db.update("INSERT INTO store_cart_item(user_id,store_id,item_type,item_id,quantity,flavors,flavor_hash) VALUES(?,?,'SETMEAL',?,1,'{}',?)",
        other, store, meal, com.sky.common.Hashes.sha256("{}"));
    @SuppressWarnings("unchecked") var items = (List<Map<String, Object>>) carts.all().getFirst().get("items");
    assertEquals(1, items.size());
    assertEquals(dish, Db.id(items.getFirst(), "item_id"));
    db.update("DELETE FROM dish WHERE id=?", dish);
    assertEquals("NOT_FOUND", assertThrows(com.sky.common.BusinessException.class, carts::all).code());
  }
  @Test void liveSchemaCanBeExportedFromTheIsolatedApplication() throws Exception {
    String token = auth.issue("ADMIN", 1).get("token").toString();
    String response = mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertTrue(response.contains("/api/v1/user/orders"));
    String destination = System.getProperty("campus.schema.output");
    if (destination != null) Files.writeString(Path.of(destination), response);
  }
  @Test void explicitContractsMatchActualDatabaseProjectionsWithoutWideningFields() {
    long order = db.insert("INSERT INTO orders(number,user_id,address_book_id,order_time,amount,status,store_id,campus_id,paid_cents)"
        + " VALUES(?,?,0,NOW(),12,3,?,?,1200)", "typed-" + UUID.randomUUID(), user, store, campus);
    db.update("INSERT INTO order_detail(order_id,dish_id,name,number,amount,line_total_cents) VALUES(?,?,'快照菜品',2,6,1200)", order, dish);
    db.update("INSERT INTO delivery_task(order_id,campus_id,state) VALUES(?,?,'OPEN')", order, campus);
    long sale = db.insert("INSERT INTO aftersale(order_id,user_id,store_id,kind,state,reason,amount_cents,created_at) VALUES(?,?,?,'CANCEL','REJECTED','契约验证',100,NOW())", order, user, store);
    db.update("INSERT INTO refund_request(order_id,aftersale_id,store_id,amount_cents,next_attempt_at,created_at) VALUES(?,?,?,100,NOW(),NOW())", order, sale, store);
    var raw = fulfillment.orders(null);
    assertEquals(com.sky.web.ApiAdvice.wire(raw), com.sky.web.ApiAdvice.wire(com.sky.web.PlatformViewFactory.orders(raw)));
    assertEquals(com.sky.web.ApiAdvice.wire(fulfillment.detail(order)), com.sky.web.ApiAdvice.wire(com.sky.web.PlatformViewFactory.order(fulfillment.detail(order))));
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new Actor(1, "ADMIN", "PLATFORM_ADMIN", "", false), null, List.of()));
    var refunds = jobs.refunds(null);
    assertEquals(com.sky.web.ApiAdvice.wire(refunds), com.sky.web.ApiAdvice.wire(com.sky.web.PlatformViewFactory.refunds(refunds)));
    var stores = discovery.stores(campus, "", null, null, null, false);
    assertEquals(com.sky.web.ApiAdvice.wire(stores), com.sky.web.ApiAdvice.wire(com.sky.web.PlatformViewFactory.stores(stores)));
    db.update("UPDATE dish SET status=1 WHERE id=?", dish);
    db.update("INSERT INTO dish_flavor(dish_id,name,value) VALUES(?,'辣度','[\"微辣\"]')", dish);
    db.update("INSERT INTO setmeal_dish(setmeal_id,dish_id,name,price,copies) VALUES(?,?,'菜品',12,1)", meal, dish);
    var products = discovery.search(campus, "", null, null, null, null);
    assertEquals(2, products.records().size());
    assertEquals(com.sky.web.ApiAdvice.wire(products), com.sky.web.ApiAdvice.wire(com.sky.web.PlatformViewFactory.search(products)));
  }
}
