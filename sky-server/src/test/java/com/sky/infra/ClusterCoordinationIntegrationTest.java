package com.sky.infra;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.business.MenuCache;
import com.sky.common.BusinessException;
import com.sky.security.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class ClusterCoordinationIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("cluster_test")
          .withUsername("test")
          .withPassword("test")
          .withCreateContainerCmdModifier(
              command -> command.getHostConfig().withMemory(768L * 1024 * 1024));

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  static Db db;
  static DataSourceTransactionManager manager;
  static StringRedisTemplate template;
  static LettuceConnectionFactory connection;

  @BeforeAll
  static void setup() {
    var source =
        new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    db = new Db(new JdbcTemplate(source));
    assertEquals(
        0,
        db.count("SELECT COUNT(*) FROM employee"),
        "Migrations must not seed a shared administrator");
    manager = new DataSourceTransactionManager(source);
    connection = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    connection.afterPropertiesSet();
    connection.start();
    template = new StringRedisTemplate(connection);
    template.afterPropertiesSet();
    db.update("CREATE TABLE cluster_test_menu(id BIGINT PRIMARY KEY,name VARCHAR(80) NOT NULL)");
    db.update("INSERT INTO cluster_test_menu VALUES(1,'before')");
  }

  @AfterAll
  static void close() {
    if (connection != null) connection.destroy();
  }

  MenuCache node() {
    var cache =
        new MenuCache(
            template,
            new Json(new ObjectMapper()),
            db,
            Clock.systemUTC(),
            manager,
            new RedisAccess());
    ReflectionTestUtils.setField(cache, "sharedVersions", true);
    return cache;
  }

  @Test
  void twoCacheNodesObserveOneTransactionalVersionAndRejectLateOldFill() throws Exception {
    MenuCache first = node(), second = node();
    String key = "integration-shared-" + UUID.randomUUID();
    var entered = new CountDownLatch(1);
    var resume = new CountDownLatch(1);
    try (var pool = Executors.newSingleThreadExecutor()) {
      Future<List<Map<String, Object>>> old =
          pool.submit(
              () ->
                  first.load(
                      key,
                      () -> {
                        var snapshot = db.rows("SELECT name FROM cluster_test_menu WHERE id=1");
                        entered.countDown();
                        try {
                          assertTrue(resume.await(10, TimeUnit.SECONDS));
                        } catch (InterruptedException exception) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(exception);
                        }
                        return snapshot;
                      }));
      assertTrue(entered.await(10, TimeUnit.SECONDS));
      new TransactionTemplate(manager)
          .executeWithoutResult(
              status -> {
                db.update("UPDATE cluster_test_menu SET name='after' WHERE id=1");
                first.afterCommit(key);
              });
      assertEquals(
          "after",
          second
              .load(key, () -> db.rows("SELECT name FROM cluster_test_menu WHERE id=1"))
              .getFirst()
              .get("name"));
      resume.countDown();
      assertEquals("before", old.get(10, TimeUnit.SECONDS).getFirst().get("name"));
      assertEquals(
          "after",
          first
              .load(key, () -> fail("New version must already be shared in Redis"))
              .getFirst()
              .get("name"));
    } finally {
      resume.countDown();
    }
    assertEquals(
        1, db.count("SELECT generation FROM catalog_cache_version WHERE cache_key=?", key));
    String rolledBack = key + "-rollback";
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              first.afterCommit(rolledBack);
              status.setRollbackOnly();
            });
    assertEquals(
        0, db.count("SELECT COUNT(*) FROM catalog_cache_version WHERE cache_key=?", rolledBack));
  }

  @Test
  void expiredWorkerCannotCompleteNewOwnersLease() {
    var first = new ClusterJobs(db, manager, "node-a");
    var second = new ClusterJobs(db, manager, "node-b");
    String name = "integration-lease-" + UUID.randomUUID();
    var old = first.claim(name);
    assertNotNull(old);
    assertNull(second.claim(name));
    db.update(
        "UPDATE cluster_job_lease SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE"
            + " job_name=?",
        name);
    var current = second.claim(name);
    assertNotNull(current);
    first.complete(old, "STALE_WORKER");
    assertTrue(second.owned(current));
    assertNull(second.latest(name).get("last_error"));
    second.complete(current, null);
    assertNotNull(second.latest(name).get("last_success_at"));
  }

  @Test
  void independentNodesShareAdmissionCounters() {
    var first = new RateLimits(template, Clock.systemUTC());
    var second = new RateLimits(template, Clock.systemUTC());
    String key = "integration-limit-" + UUID.randomUUID();
    first.check(key, 1, 60);
    var error = assertThrows(BusinessException.class, () -> second.check(key, 1, 60));
    assertEquals("RATE_LIMITED", error.code());
  }

  @Test
  void concurrentProductionBootstrapCreatesOnlyOneForcedChangeAdministrator() throws Exception {
    var env = new org.springframework.mock.env.MockEnvironment();
    env.setActiveProfiles("prod");
    var auth =
        new AuthService(
            db,
            Clock.systemUTC(),
            org.springframework.web.client.RestClient.create(),
            env,
            "test-bootstrap-signing-key-at-least-thirty-two-bytes",
            "",
            "");
    var first = new ProductionBootstrap(db, auth, Clock.systemUTC(), "Bootstrap!2026-unique");
    var second = new ProductionBootstrap(db, auth, Clock.systemUTC(), "Another!2026-unique");
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var jobs = new ArrayList<Future<?>>();
      for (var bootstrap : List.of(first, second))
        jobs.add(
            pool.submit(
                () -> {
                  try {
                    start.await();
                  } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                  }
                  new TransactionTemplate(manager)
                      .executeWithoutResult(
                          status ->
                              bootstrap.run(
                                  new org.springframework.boot.DefaultApplicationArguments()));
                }));
      start.countDown();
      for (var job : jobs) job.get(10, TimeUnit.SECONDS);
    }
    assertEquals(
        1,
        db.count(
            "SELECT COUNT(*) FROM employee WHERE username='admin' AND role='PLATFORM_ADMIN' AND"
                + " must_change_password=true"));
    assertTrue(
        db.one("SELECT password FROM employee WHERE username='admin'")
            .get("password")
            .toString()
            .startsWith("$argon2"));
  }

  @Test
  void productionBootstrapRefusesPreexistingDemoEmployeesWithoutChangingThem() {
    long count = db.count("SELECT COUNT(*) FROM employee");
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              db.update("UPDATE production_bootstrap_lock SET initialized_at=NULL WHERE id=1");
              db.update(
                  "INSERT INTO employee(name,username,password,role,status,phone,sex,id_number)"
                      + " VALUES('演示账户','demo-blocked','unchanged-demo-hash','PLATFORM_ADMIN',1,'','','')");
              var bootstrap =
                  new ProductionBootstrap(db, null, Clock.systemUTC(), "UnusedBootstrap!2026");
              var error =
                  assertThrows(
                      BusinessException.class,
                      () ->
                          bootstrap.run(
                              new org.springframework.boot.DefaultApplicationArguments()));
              assertEquals("PRODUCTION_DATABASE_NOT_EMPTY", error.code());
              assertEquals(
                  "unchanged-demo-hash",
                  db.one("SELECT password FROM employee WHERE username='demo-blocked'")
                      .get("password"));
              status.setRollbackOnly();
            });
    assertEquals(count, db.count("SELECT COUNT(*) FROM employee"));
  }
}
