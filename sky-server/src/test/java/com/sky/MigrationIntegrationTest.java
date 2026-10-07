package com.sky;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
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
        assertEquals("ADMIN", row.getString(1));
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
}
