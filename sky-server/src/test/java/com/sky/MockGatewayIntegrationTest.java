package com.sky;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.business.MockPaymentGateway;
import com.sky.business.PaymentGateway;
import com.sky.common.BusinessException;
import com.sky.infra.Db;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {
    "spring.profiles.active=demo",
    "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
    "campus.demo-admin-password=TestAdmin!2026",
    "campus.tasks-enabled=false"
})
@Testcontainers
class MockGatewayIntegrationTest {
  @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
      .withDatabaseName("mock_gateway_test").withUsername("test").withPassword("test")
      .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));
  @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
  }

  @Autowired Db db;
  @Autowired PlatformTransactionManager manager;
  @Autowired com.sky.platform.PlatformJobs jobs;

  @BeforeEach
  void reset() {
    db.update("DELETE FROM mock_refund_receipt");
    db.update("DELETE FROM refund_request");
  }

  @Test
  void historicalSucceededRefundIsNotSentToTheGatewayAgain() {
    db.update("INSERT INTO refund_request(order_id,store_id,amount_cents,state,next_attempt_at,created_at)"
        + " VALUES(900001,1,1500,'SUCCEEDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    assertFalse(jobs.refund());
    assertEquals(0, db.count("SELECT COUNT(*) FROM mock_refund_receipt"));
    assertEquals(1, db.count("SELECT COUNT(*) FROM refund_request WHERE state='SUCCEEDED'"));
  }

  @Test
  void committedGatewayDecisionSurvivesCallerRollbackAndGatewayRecreation() {
    var request = new PaymentGateway.Refund("refund-request:restart", "checkout:original", 1500, "CNY");
    var gateway = new MockPaymentGateway(true, db, manager);
    var receipt = new TransactionTemplate(manager).execute(status -> {
      var result = gateway.refund(request);
      status.setRollbackOnly();
      return result;
    });
    assertEquals(1, db.count("SELECT COUNT(*) FROM mock_refund_receipt"));
    var recreated = new MockPaymentGateway(true, db, manager);
    recreated.failNextRefunds(1);
    assertEquals(receipt, recreated.refund(request));
    assertEquals("REFUND_CONFLICT", assertThrows(BusinessException.class, () -> recreated.refund(
        new PaymentGateway.Refund(request.businessKey(), request.paymentKey(), 1501, "CNY"))).code());
    assertEquals("REFUND_CONFLICT", assertThrows(BusinessException.class, () -> recreated.refund(
        new PaymentGateway.Refund(request.businessKey(), "checkout:other", 1500, "CNY"))).code());
    assertEquals("MOCK_REFUND_FAILED", assertThrows(BusinessException.class, () -> recreated.refund(
        new PaymentGateway.Refund("refund-request:new", request.paymentKey(), 1500, "CNY"))).code());
    assertEquals(1, db.count("SELECT COUNT(*) FROM mock_refund_receipt"));
  }

  @Test
  void failedAndDisabledRefundsNeverLeaveSuccessfulReceipts() {
    var request = new PaymentGateway.Refund("refund-request:failed", "checkout:original", 1500, "CNY");
    var gateway = new MockPaymentGateway(true, db, manager);
    gateway.failNextRefunds(1);
    assertEquals("MOCK_REFUND_FAILED", assertThrows(BusinessException.class,
        () -> gateway.refund(request)).code());
    assertEquals(0, db.count("SELECT COUNT(*) FROM mock_refund_receipt"));
    assertEquals("MOCK_DISABLED", assertThrows(BusinessException.class,
        () -> new MockPaymentGateway(false, db, manager).refund(request)).code());
    assertEquals(0, db.count("SELECT COUNT(*) FROM mock_refund_receipt"));
    assertNotNull(gateway.refund(request));
    assertEquals(1, db.count("SELECT COUNT(*) FROM mock_refund_receipt"));
  }

  @Test
  void databaseArbitratesConcurrentIdenticalAndConflictingGatewayInstances() throws Exception {
    var request = new PaymentGateway.Refund("refund-request:race", "checkout:original", 1500, "CNY");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var results = new ArrayList<Future<PaymentGateway.Receipt>>();
      for (int i = 0; i < 2; i++) results.add(pool.submit(() -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return new MockPaymentGateway(true, db, manager).refund(request);
      }));
      start.countDown();
      assertEquals(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));
      assertEquals(1, db.count("SELECT COUNT(*) FROM mock_refund_receipt"));
      db.update("DELETE FROM mock_refund_receipt");
      var race = new CountDownLatch(1);
      var outcomes = new ArrayList<Future<String>>();
      for (int i = 0; i < 2; i++) {
        long amount = 1500 + i;
        outcomes.add(pool.submit(() -> {
          assertTrue(race.await(5, TimeUnit.SECONDS));
          try {
            new MockPaymentGateway(true, db, manager).refund(new PaymentGateway.Refund(
                request.businessKey(), request.paymentKey(), amount, request.currency()));
            return "SUCCESS";
          } catch (BusinessException ex) {
            return ex.code();
          }
        }));
      }
      race.countDown();
      var observed = new ArrayList<String>();
      for (var outcome : outcomes) observed.add(outcome.get(10, TimeUnit.SECONDS));
      assertEquals(Set.of("SUCCESS", "REFUND_CONFLICT"), new HashSet<>(observed));
      assertEquals(1, db.count("SELECT COUNT(*) FROM mock_refund_receipt"));
    }
  }
}
