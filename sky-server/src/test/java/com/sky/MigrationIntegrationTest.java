package com.sky;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class MigrationIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withUsername("root")
          .withPassword("test")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));

  String create(String name) throws Exception {
    try (var connection =
            DriverManager.getConnection(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE " + name + " CHARACTER SET utf8mb4");
    }
    return mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + name);
  }

  void legacy(String url) throws Exception {
    try (var c = DriverManager.getConnection(url, "root", "test")) {
      ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V1__legacy_schema.sql"));
      try (var s = c.createStatement()) {
        s.execute(
            "INSERT INTO employee(id,name,username,password,phone,sex,id_number)"
                + " VALUES(1,'旧管理员','admin',MD5('LegacyPass!2026'),'13800000000','1','')");
        s.execute("INSERT INTO user(id,openid,name) VALUES(1,'legacy-openid','历史用户')");
        s.execute(
            "INSERT INTO address_book(id,user_id,consignee,phone,detail)"
                + " VALUES(1,1,'历史用户','13800000000','旧宿舍地址')");
        s.execute(
            "INSERT INTO orders(id,number,user_id,address_book_id,order_time,amount)"
                + " VALUES(1,'legacy-order',1,1,'2025-01-01 12:00:00',20.00)");
      }
    }
  }

  @Test
  void v8ToV9OnlyAddsIndexesAndPreservesBusinessRows() throws Exception {
    String url = create("v8_history_upgrade");
    Flyway.configure().dataSource(url, "root", "test").target("8").load().migrate();
    try (var c = DriverManager.getConnection(url, "root", "test");
        var s = c.createStatement()) {
      s.execute("INSERT INTO user(id,openid,name) VALUES(1,'upgrade-v8','升级用户')");
      s.execute("INSERT INTO category(id,store_id,type,name,status) VALUES(1,1,1,'分类',1)");
      s.execute("INSERT INTO dish(id,store_id,category_id,name,price) VALUES(1,1,1,'菜品',5)");
      s.execute("INSERT INTO store_cart_state VALUES(1,1,4)");
      s.execute(
          "INSERT INTO"
              + " store_cart_item(user_id,store_id,item_type,item_id,flavors,flavor_hash,quantity)"
              + " VALUES(1,1,'DISH',1,'{}',REPEAT('a',64),2)");
      s.execute(
          "INSERT INTO"
              + " checkout(id,user_id,request_id,request_hash,state,total_cents,paid_at,expires_at,created_at,quote_json)"
              + " VALUES(1,1,'stable-key',REPEAT('b',64),'PAID',1000,'2026-10-15"
              + " 12:01:00','2026-10-15 12:15:00','2026-10-15 12:00:00','{}')");
      s.execute(
          "INSERT INTO"
              + " orders(id,number,status,user_id,address_book_id,order_time,amount,store_id,campus_id,checkout_id,paid_cents,reserved_refund_cents)"
              + " VALUES(1,'v8-order',5,1,0,'2026-10-15 12:00:00',10,1,1,1,1000,500)");
      s.execute(
          "INSERT INTO"
              + " order_detail(id,order_id,dish_id,name,number,amount,line_total_cents,reserved_quantity)"
              + " VALUES(1,1,1,'原商品快照',2,5,1000,1)");
      s.execute("INSERT INTO daily_quota VALUES('2026-10-15',1,10,0,2)");
      s.execute("INSERT INTO order_reservation VALUES(1,1,'2026-10-15',2,'CONSUMED')");
      s.execute(
          "INSERT INTO payment_attempt(order_id,gateway_ref,created_at)"
              + " VALUES(1,'immutable-ref','2026-10-15 12:01:00')");
      s.execute(
          "INSERT INTO"
              + " aftersale(id,order_id,user_id,store_id,kind,state,reason,amount_cents,created_at)"
              + " VALUES(1,1,1,1,'PARTIAL','APPROVED','升级前申请',500,'2026-10-15 13:00:00')");
      s.execute("INSERT INTO aftersale_item VALUES(1,1,1,500)");
      s.execute(
          "INSERT INTO"
              + " refund_request(order_id,aftersale_id,store_id,amount_cents,state,attempts,next_attempt_at,lease_token,lease_until,created_at)"
              + " VALUES(1,1,1,500,'PROCESSING',2,'2026-10-15"
              + " 13:00:00','lease-before-upgrade','2026-10-15 13:00:30','2026-10-15 13:00:00')");
      s.execute(
          "INSERT INTO"
              + " outbox_event(event_type,aggregate_id,payload,state,next_attempt_at,created_at,store_id,campus_id)"
              + " VALUES('ORDER_PAID',1,'{\"orderId\":\"1\"}','FAILED','2026-10-15"
              + " 12:01:00','2026-10-15 12:01:00',1,1)");
    }
    var before = businessRows(url);
    assertEquals(1, before.get("orders").size());
    assertEquals(1, before.get("refund_request").size());
    var result = Flyway.configure().dataSource(url, "root", "test").load().migrate();
    assertEquals(1, result.migrationsExecuted);
    assertEquals(before, businessRows(url));
    try (var c = DriverManager.getConnection(url, "root", "test");
        var s = c.createStatement();
        var rows =
            s.executeQuery(
                "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.statistics WHERE"
                    + " table_schema='v8_history_upgrade' AND INDEX_NAME IN"
                    + " ('ix_aftersale_user_history','ix_refund_store_history','ix_dish_discovery','ix_setmeal_discovery')")) {
      rows.next();
      assertEquals(4, rows.getInt(1));
    }
  }

  private Map<String, List<List<String>>> businessRows(String url) throws Exception {
    var result = new TreeMap<String, List<List<String>>>();
    try (var c = DriverManager.getConnection(url, "root", "test");
        var tables = c.getMetaData().getTables(c.getCatalog(), null, "%", new String[] {"TABLE"})) {
      while (tables.next()) {
        String table = tables.getString("TABLE_NAME");
        if (table.equals("flyway_schema_history")) continue;
        var rows = new ArrayList<List<String>>();
        try (var s = c.createStatement();
            var data = s.executeQuery("SELECT * FROM `" + table + "`")) {
          while (data.next()) {
            var row = new ArrayList<String>();
            for (int i = 1; i <= data.getMetaData().getColumnCount(); i++)
              row.add(data.getString(i));
            rows.add(row);
          }
        }
        rows.sort(Comparator.comparing(Object::toString));
        result.put(table, rows);
      }
    }
    return result;
  }

  @Test
  void explicitBaselinePreservesHistoricalDataAndMarksBackfill() throws Exception {
    String url = create("legacy_sample");
    legacy(url);
    var flyway =
        Flyway.configure()
            .dataSource(url, "root", "test")
            .baselineVersion(MigrationVersion.fromVersion("1"))
            .load();
    assertThrows(Exception.class, flyway::migrate);
    flyway.baseline();
    flyway.migrate();
    try (var c = DriverManager.getConnection(url, "root", "test");
        var s = c.createStatement()) {
      try (var row =
          s.executeQuery("SELECT number,amount,address,snapshot_source FROM orders WHERE id=1")) {
        assertTrue(row.next());
        assertEquals("legacy-order", row.getString(1));
        assertEquals("20.00", row.getBigDecimal(2).toPlainString());
        assertEquals("旧宿舍地址", row.getString(3));
        assertEquals("LEGACY_BACKFILL", row.getString(4));
      }
      try (var row = s.executeQuery("SELECT COUNT(*) FROM order_reservation")) {
        row.next();
        assertEquals(0, row.getInt(1));
      }
      try (var row = s.executeQuery("SELECT role FROM employee WHERE id=1")) {
        row.next();
        assertEquals("PLATFORM_ADMIN", row.getString(1));
      }
    }
  }

  @Test
  void duplicateIdentityStopsMigrationWithoutDeletingRecords() throws Exception {
    String url = create("duplicate_sample");
    legacy(url);
    try (var c = DriverManager.getConnection(url, "root", "test");
        var s = c.createStatement()) {
      s.execute("INSERT INTO user(openid,name) VALUES('legacy-openid','重复用户')");
    }
    var flyway =
        Flyway.configure()
            .dataSource(url, "root", "test")
            .baselineVersion(MigrationVersion.fromVersion("1"))
            .load();
    flyway.baseline();
    assertThrows(Exception.class, flyway::migrate);
    try (var c = DriverManager.getConnection(url, "root", "test");
        var s = c.createStatement();
        var row = s.executeQuery("SELECT COUNT(*) FROM user")) {
      row.next();
      assertEquals(2, row.getInt(1));
    }
  }

  @Test
  void v4UpgradePreservesCartsPaymentsAndPendingRefundIdentity() throws Exception {
    String url = create("v4_platform_upgrade");
    Flyway.configure().dataSource(url, "root", "test").target("4").load().migrate();
    try (var c = DriverManager.getConnection(url, "root", "test");
        var st = c.createStatement()) {
      st.execute(
          "INSERT INTO employee(id,name,username,password,role,phone,sex,id_number)"
              + " VALUES(1,'旧管理员','admin','hash','ADMIN','','','')");
      st.execute("INSERT INTO user(id,openid,name) VALUES(1,'upgrade-user','历史用户')");
      st.execute("INSERT INTO category(id,type,name,status) VALUES(1,1,'旧分类',1)");
      st.execute("INSERT INTO dish(id,category_id,name,price) VALUES(2,1,'旧菜品',12)");
      st.execute("INSERT INTO cart_state VALUES(1,7)");
      st.execute(
          "INSERT INTO cart_item(user_id,item_type,item_id,flavors,flavor_hash,quantity)"
              + " VALUES(1,'DISH',2,'{}',REPEAT('a',64),2)");
      st.execute(
          "INSERT INTO"
              + " orders(id,number,status,user_id,address_book_id,order_time,pay_status,amount,packaging_fee,snapshot_source,address)"
              + " VALUES(9,'preserved-order',6,1,0,'2026-10-14"
              + " 12:00:00',1,26,2,'ORIGINAL','原始快照地址')");
      st.execute(
          "INSERT INTO order_detail(order_id,dish_id,name,number,amount) VALUES(9,2,'旧菜品',2,12)");
      st.execute(
          "INSERT INTO payment_attempt(order_id,gateway_ref,created_at)"
              + " VALUES(9,'MOCK-OLD-9','2026-10-14 12:01:00')");
      st.execute(
          "INSERT INTO refund_task(id,order_id,state,next_attempt_at,created_at)"
              + " VALUES(42,9,'REQUESTED','2026-10-14 12:02:00','2026-10-14 12:02:00')");
    }
    Flyway.configure().dataSource(url, "root", "test").load().migrate();
    try (var c = DriverManager.getConnection(url, "root", "test");
        var st = c.createStatement()) {
      try (var r =
          st.executeQuery(
              "SELECT"
                  + " o.number,o.address,o.amount,o.store_id,o.campus_id,c.total_cents,o.paid_cents,o.reserved_refund_cents"
                  + " FROM orders o JOIN checkout c ON c.id=o.checkout_id WHERE o.id=9")) {
        assertTrue(r.next());
        assertEquals("preserved-order", r.getString(1));
        assertEquals("原始快照地址", r.getString(2));
        assertEquals("26.00", r.getBigDecimal(3).toPlainString());
        assertEquals(1, r.getLong(4));
        assertEquals(1, r.getLong(5));
        assertEquals(2600, r.getLong(6));
        assertEquals(2600, r.getLong(7));
        assertEquals(2600, r.getLong(8));
      }
      try (var r =
          st.executeQuery("SELECT version FROM store_cart_state WHERE user_id=1 AND store_id=1")) {
        assertTrue(r.next());
        assertEquals(7, r.getLong(1));
      }
      try (var r = st.executeQuery("SELECT id,order_id,state,amount_cents FROM refund_request")) {
        assertTrue(r.next());
        assertEquals(42, r.getLong(1));
        assertEquals(9, r.getLong(2));
        assertEquals("REQUESTED", r.getString(3));
        assertEquals(2600, r.getLong(4));
        assertFalse(r.next());
      }
      try (var r = st.executeQuery("SELECT gateway_ref FROM payment_attempt WHERE order_id=9")) {
        assertTrue(r.next());
        assertEquals("MOCK-OLD-9", r.getString(1));
        assertFalse(r.next());
      }
      try (var r = st.executeQuery("SELECT paid_at,paid_time_source FROM checkout WHERE id=9")) {
        assertTrue(r.next());
        assertNull(r.getTimestamp(1));
        assertEquals("LEGACY_UNKNOWN", r.getString(2));
      }
    }
  }
}
