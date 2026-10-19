package com.sky;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.infra.Db;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false"
    })
@Testcontainers
class DatabaseTimeoutIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("timeout_test")
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

  @Autowired Db db;
  @Autowired DataSource dataSource;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  void prepareIsolatedTable() {
    db.update("CREATE TABLE IF NOT EXISTS timeout_probe(id INT PRIMARY KEY, value INT NOT NULL)");
    db.update("REPLACE INTO timeout_probe VALUES (1,0),(2,0)");
  }

  @Test
  void actualManagerAndBothJdbcPathsApplyConfiguredBudgets() throws Exception {
    var manager = assertInstanceOf(DataSourceTransactionManager.class, transactionManager);
    assertEquals(10, manager.getDefaultTimeout());
    assertEquals(5, db.jdbc.getQueryTimeout());
    assertSame(db.jdbc, db.named.getJdbcTemplate());
    HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
    assertEquals(3000, pool.getConnectionTimeout());
    assertEquals("2000", pool.getDataSourceProperties().getProperty("connectTimeout"));
    assertEquals("12000", pool.getDataSourceProperties().getProperty("socketTimeout"));
    assertEquals(3, db.count("SELECT @@SESSION.innodb_lock_wait_timeout"));
    assertEquals(
        5,
        db.jdbc.execute("SELECT 1", (PreparedStatementCallback<Integer>) s -> s.getQueryTimeout()));
    var tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(
        status -> {
          int seconds =
              db.jdbc.execute(
                  "SELECT 1", (PreparedStatementCallback<Integer>) s -> s.getQueryTimeout());
          assertTrue(seconds > 0 && seconds <= 10);
          assertTrue(
              seconds > 5, "transaction budget must override the non-transaction query limit");
        });
  }

  @Test
  void plainAndNamedSlowSqlAreCancelledWithoutHoldingConnections() {
    assertTimeout(
        Duration.ofSeconds(9),
        () -> assertThrows(QueryTimeoutException.class, () -> db.count("SELECT SLEEP(20)")));
    assertTimeout(
        Duration.ofSeconds(9),
        () ->
            assertThrows(
                QueryTimeoutException.class,
                () ->
                    db.named.queryForObject(
                        "SELECT SLEEP(:seconds)", Map.of("seconds", 20), Long.class)));
    assertEquals(1, db.count("SELECT 1"));
    assertFalse(
        org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(
            dataSource));
  }

  @Test
  void transactionTimeoutRollsBackEarlierWriteAndReleasesConnection() {
    var tx = new TransactionTemplate(transactionManager);
    tx.setTimeout(1);
    assertTimeout(
        Duration.ofSeconds(5),
        () ->
            assertThrows(
                QueryTimeoutException.class,
                () ->
                    tx.executeWithoutResult(
                        status -> {
                          db.update("UPDATE timeout_probe SET value=1 WHERE id=1");
                          db.named.queryForObject(
                              "SELECT SLEEP(:seconds)", Map.of("seconds", 10), Long.class);
                        })));
    assertEquals(0, db.count("SELECT value FROM timeout_probe WHERE id=1"));
    assertEquals(1, db.count("SELECT 1"));
    assertFalse(
        org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(
            dataSource));
  }

  @Test
  void rowLockWaitIsBoundedAndWholeTransactionRollsBack() throws Exception {
    try (Connection blocker = dataSource.getConnection()) {
      blocker.setAutoCommit(false);
      try {
        try (var s = blocker.prepareStatement("UPDATE timeout_probe SET value=10 WHERE id=1")) {
          s.executeUpdate();
        }
        var tx = new TransactionTemplate(transactionManager);
        assertTimeout(
            Duration.ofSeconds(7),
            () ->
                assertThrows(
                    CannotAcquireLockException.class,
                    () ->
                        tx.executeWithoutResult(
                            status -> {
                              db.update("UPDATE timeout_probe SET value=2 WHERE id=2");
                              db.update("UPDATE timeout_probe SET value=2 WHERE id=1");
                            })));
      } finally {
        blocker.rollback();
      }
    }
    assertEquals(0, db.count("SELECT SUM(value) FROM timeout_probe"));
    assertEquals(1, db.count("SELECT 1"));
  }
}
