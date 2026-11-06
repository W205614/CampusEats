package com.sky.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sky.common.BusinessException;
import com.sky.infra.*;
import com.sky.platform.*;
import com.sky.security.Actor;
import com.sun.net.httpserver.*;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Real MySQL, Redis, APIv3 request signatures, signed HTTP responses and encrypted notifications.
 */
@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=test-only-secret-thirty-two-bytes-long-value",
      "campus.demo-admin-password=TestAdmin!2026",
      "campus.tasks-enabled=false",
      "campus.payment.provider=wechat"
    })
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentIntegrationTest {
  static final Fixture fixture = new Fixture();

  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("payments_test")
          .withUsername("test")
          .withPassword("test")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", mysql::getJdbcUrl);
    r.add("spring.datasource.username", mysql::getUsername);
    r.add("spring.datasource.password", mysql::getPassword);
    r.add("spring.data.redis.host", redis::getHost);
    r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    r.add("campus.payment.merchant-id", () -> "1900000109");
    r.add("campus.payment.serial", () -> "MERCHANT_SERIAL");
    r.add("campus.payment.private-key-path", () -> fixture.privateKeyPath);
    r.add("campus.payment.public-key-path", () -> fixture.publicKeyPath);
    r.add("campus.payment.public-key-id", () -> Fixture.PUBLIC_ID);
    r.add("campus.payment.api-v3-key", () -> Fixture.AES);
    r.add("campus.payment.mini-app-id", () -> "wx-mini-test");
    r.add("campus.payment.mp-app-id", () -> "wx-mp-test");
    r.add("campus.payment.notify-base", () -> "https://campuseats.example.test");
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    WechatPayChannel fixtureChannel(WechatPaySettings settings, Json json, Clock clock) {
      var config =
          new RSAPublicKeyConfig.Builder()
              .merchantId(settings.merchantId)
              .merchantSerialNumber(settings.serial)
              .privateKeyFromPath(settings.privateKey)
              .publicKeyFromPath(settings.publicKey)
              .publicKeyId(settings.publicKeyId)
              .apiV3Key(settings.apiV3Key)
              .build();
      var transport =
          new okhttp3.OkHttpClient.Builder()
              .addInterceptor(
                  chain -> {
                    var request = chain.request();
                    if (request.url().host().equals("api.mch.weixin.qq.com"))
                      request =
                          request
                              .newBuilder()
                              .url(
                                  fixture.base
                                      + request.url().encodedPath()
                                      + (request.url().encodedQuery() == null
                                          ? ""
                                          : "?" + request.url().encodedQuery()))
                              .build();
                    var response = chain.proceed(request);
                    var normalized = response.newBuilder();
                    // JDK HttpServer rewrites header casing; the official SDK preserves the
                    // vendor's canonical names.
                    for (String name :
                        List.of(
                            "Wechatpay-Timestamp",
                            "Wechatpay-Nonce",
                            "Wechatpay-Serial",
                            "Wechatpay-Signature",
                            "Content-Type")) {
                      String value = response.header(name);
                      if (value != null) normalized.header(name, value);
                    }
                    return normalized.build();
                  })
              .build();
      return new WechatPayChannel(
          settings,
          json,
          clock,
          config,
          new DefaultHttpClientBuilder()
              .config(config)
              .okHttpClient(transport)
              .connectTimeoutMs(1000)
              .readTimeoutMs(1000)
              .disableRetryOnConnectionFailure()
              .build(),
          fixture.base) {
        @Override
        public Map<String, Object> request(String method, String path, Map<String, Object> body) {
          try {
            return super.request(method, path, body);
          } catch (RuntimeException ex) {
            fixture.lastFailure.set(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw ex;
          }
        }
      };
    }
  }

  @Autowired Db db;
  @Autowired PaymentService payments;
  @Autowired ChannelRefunds refunds;
  @Autowired PlatformJobs jobs;
  @Autowired CheckoutService checkouts;
  @Autowired MockMvc mvc;
  @Autowired ChannelFinance finance;
  @Autowired com.sky.integration.IntegrationReadiness readiness;
  long user, store, other, checkout, order;

  void actor(String type, long id) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new Actor(id, type, type.equals("USER") ? "USER" : "PLATFORM_ADMIN", "", false),
                null,
                List.of()));
  }

  @BeforeEach
  void setup() {
    fixture.dropPrepay.set(false);
    fixture.failClose.set(false);
    fixture.dropRefund.set(false);
    fixture.badBillHash.set(false);
    user = db.insert("INSERT INTO user(name,create_time) VALUES('channel-user',NOW())");
    db.update("DELETE FROM channel_refund");
    db.update("DELETE FROM refund_request");
    store = db.insert("INSERT INTO store(campus_id,name) VALUES(1,?)", "channel-store-" + user);
    other = db.insert("INSERT INTO store(campus_id,name) VALUES(1,?)", "channel-other-" + user);
    for (long id : List.of(store, other))
      db.update(
          "INSERT INTO merchant_payment_config(store_id,sub_mchid,enabled,updated_at) VALUES(?,?"
              + " ,true,NOW())",
          id,
          "123456" + id);
    checkout = newCheckout(1500);
    order = newOrder(store, 1000);
    newOrder(other, 500);
    actor("USER", user);
  }

  long newCheckout(long amount) {
    var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    return db.insert(
        "INSERT INTO"
            + " checkout(user_id,request_id,request_hash,state,total_cents,expires_at,created_at,quote_json,payment_provider)"
            + " VALUES(?,?,?,'PENDING',?,?,?,'{}','WECHAT')",
        user,
        UUID.randomUUID().toString(),
        "0".repeat(64),
        amount,
        now.plusMinutes(15),
        now);
  }

  long newOrder(long store, long amount) {
    return db.insert(
        "INSERT INTO"
            + " orders(number,status,user_id,address_book_id,order_time,amount,store_id,campus_id,checkout_id,fulfillment)"
            + " VALUES(?,1,?,0,NOW(),?,?,1,?,'PICKUP')",
        UUID.randomUUID().toString(),
        user,
        BigDecimal.valueOf(amount, 2),
        store,
        checkout);
  }

  PaymentService.View start() {
    return payments.create(checkout, "p" + checkout, "NATIVE", 0, "127.0.0.1");
  }

  void callback(String id) {
    var notice = fixture.notice(fixture.paid(id), "TRANSACTION.SUCCESS");
    payments.notification(
        Fixture.PUBLIC_ID, notice.timestamp(), notice.nonce(), notice.signature(), notice.body());
  }

  long ledger(String type) {
    return db.count(
        "SELECT COUNT(*) FROM finance_ledger WHERE store_id IN (?,?) AND source_type=?",
        store,
        other,
        type);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @AfterAll
  static void stop() {
    fixture.server.stop(0);
  }

  @Test
  void realSignedPrepayAndEncryptedCallbackAreIdempotent() throws Exception {
    var intent = start();
    assertEquals("PENDING", intent.state(), intent.lastError() + " " + fixture.lastFailure.get());
    assertEquals("qrCode", intent.action().get("type"));
    int calls = fixture.prepayCount.get();
    assertEquals(
        intent.id(), payments.create(checkout, "p" + checkout, "NATIVE", 999, "127.0.0.1").id());
    assertEquals(calls, fixture.prepayCount.get());
    assertEquals("PENDING", checkouts.detail(checkout).get("state"));
    assertEquals(0, ledger("PAYMENT"));
    var notice = fixture.notice(fixture.paid(intent.id()), "TRANSACTION.SUCCESS");
    for (int repeat = 0; repeat < 2; repeat++)
      mvc.perform(
              post("/api/v1/payments/wechat/notify")
                  .contentType("application/json")
                  .header("Wechatpay-Serial", Fixture.PUBLIC_ID)
                  .header("Wechatpay-Timestamp", notice.timestamp())
                  .header("Wechatpay-Nonce", notice.nonce())
                  .header("Wechatpay-Signature", notice.signature())
                  .content(notice.body()))
          .andExpect(status().isNoContent())
          .andExpect(content().string(""));
    actor("USER", user);
    assertEquals("PAID", checkouts.detail(checkout).get("state"));
    assertEquals(2, ledger("PAYMENT"));
    assertEquals(
        1, db.count("SELECT COUNT(*) FROM payment_callback WHERE event_id=?", notice.event()));
    assertTrue(fixture.verified.get() > 0);
  }

  @Test
  void tamperedSignatureAndSignedWrongAmountNeverRecordPayment() {
    var intent = start();
    var data = fixture.paid(intent.id());
    var notice = fixture.notice(data, "TRANSACTION.SUCCESS");
    assertThrows(
        BusinessException.class,
        () ->
            payments.notification(
                Fixture.PUBLIC_ID,
                notice.timestamp(),
                notice.nonce(),
                notice.signature(),
                notice.body() + " "));
    @SuppressWarnings("unchecked")
    var parts = (List<Map<String, Object>>) data.get("sub_orders");
    parts.getFirst().put("amount", Map.of("total_amount", 1, "currency", "CNY"));
    var wrong = fixture.notice(data, "TRANSACTION.SUCCESS");
    assertEquals(
        "PAYMENT_MISMATCH",
        assertThrows(
                BusinessException.class,
                () ->
                    payments.notification(
                        Fixture.PUBLIC_ID,
                        wrong.timestamp(),
                        wrong.nonce(),
                        wrong.signature(),
                        wrong.body()))
            .code());
    assertEquals(0, ledger("PAYMENT"));
    assertEquals("PENDING", checkouts.detail(checkout).get("state"));
  }

  @Test
  void responseLossRecoversPersistedIntentByQueryWithoutAnotherPrepay() {
    fixture.dropPrepay.set(true);
    var intent = start();
    assertEquals("UNKNOWN", intent.state());
    int calls = fixture.prepayCount.get();
    fixture.paid(intent.id());
    payments.drive(intent.id());
    assertEquals("SUCCEEDED", payments.status(checkout).state());
    assertEquals(calls, fixture.prepayCount.get());
    assertEquals(2, ledger("PAYMENT"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"REFUND", "PAYERROR", "FUTURE_STATE"})
  void unconfirmedChannelStatesUseTheBoundedReviewFlowInsteadOfResettingAsPending(String state) {
    var intent = start();
    fixture.states.put(intent.id(), state);
    for (int attempt = 0; attempt < 5; attempt++) payments.drive(intent.id());
    var current = payments.status(checkout);
    assertEquals("REVIEW", current.state());
    assertEquals("PAYMENT_CHANNEL_STATE", current.lastError());
    assertTrue(current.action().isEmpty());
    assertEquals("PENDING", checkouts.detail(checkout).get("state"));
    assertEquals(0, ledger("PAYMENT"));
    assertEquals(state, fixture.states.get(intent.id()));
    assertEquals(5, db.count("SELECT attempts FROM payment_intent WHERE id=?", intent.id()));
  }

  @Test
  void cancellationKeepsReservationUntilSignedCloseCompletes() {
    var intent = start();
    fixture.failClose.set(true);
    payments.cancel(checkout, 0);
    assertTrue(payments.status(checkout).closeRequested());
    assertTrue(payments.status(checkout).action().isEmpty());
    assertEquals(
        true,
        checkouts.list().stream()
            .filter(c -> Db.id(c, "id") == checkout)
            .findFirst()
            .orElseThrow()
            .get("payment_close_requested"));
    assertEquals("PENDING", checkouts.detail(checkout).get("state"));
    assertEquals(1, Db.integer(db.one("SELECT status FROM orders WHERE id=?", order), "status"));
    fixture.failClose.set(false);
    payments.drive(intent.id());
    assertEquals("CANCELLED", checkouts.detail(checkout).get("state"));
    assertEquals("CLOSED", payments.status(checkout).state());
    assertEquals(0, ledger("PAYMENT"));
  }

  @Test
  void lostUnpaidPrepayResponseRestoresActionOnlyAfterQueryingTheOriginalOrder() {
    fixture.dropPrepay.set(true);
    var intent = start();
    assertEquals("UNKNOWN", intent.state());
    int requests = fixture.prepayCount.get();
    payments.drive(intent.id());
    var restored = payments.status(checkout);
    assertEquals("PENDING", restored.state());
    assertEquals("qrCode", restored.action().get("type"));
    assertEquals(requests + 1, fixture.prepayCount.get());
    assertEquals("PENDING", checkouts.detail(checkout).get("state"));
  }

  @Test
  void paymentWinningCancellationIsConfirmedRatherThanReleased() {
    var intent = start();
    fixture.paid(intent.id());
    payments.cancel(checkout, 0);
    assertEquals("PAID", checkouts.detail(checkout).get("state"));
    assertEquals(2, ledger("PAYMENT"));
  }

  @Test
  void acceptedRefundIsNotSuccessfulUntilChannelConfirmation() {
    var intent = start();
    callback(intent.id());
    long request =
        db.insert(
            "INSERT INTO"
                + " refund_request(order_id,store_id,amount_cents,state,next_attempt_at,created_at)"
                + " VALUES(?,?,500,'REQUESTED',NOW(),NOW())",
            order,
            store);
    db.update("UPDATE orders SET reserved_refund_cents=500 WHERE id=?", order);
    assertTrue(jobs.refund());
    assertEquals(
        0, Db.id(db.one("SELECT refunded_cents FROM orders WHERE id=?", order), "refunded_cents"));
    assertEquals(0, ledger("REFUND"));
    fixture.refundStates.put("CER" + request, "SUCCESS");
    db.update("UPDATE refund_request SET next_attempt_at=NOW() WHERE id=?", request);
    assertTrue(jobs.refund());
    assertEquals(
        500,
        Db.id(db.one("SELECT refunded_cents FROM orders WHERE id=?", order), "refunded_cents"));
    assertEquals(1, ledger("REFUND"));
    assertFalse(jobs.refund());
  }

  @Test
  void refundPostResponseLossUsesOriginalIdentifierAndNeverDoubleRefunds() {
    var intent = start();
    callback(intent.id());
    long request =
        db.insert(
            "INSERT INTO"
                + " refund_request(order_id,store_id,amount_cents,state,next_attempt_at,created_at)"
                + " VALUES(?,?,200,'REQUESTED',NOW(),NOW())",
            order,
            store);
    db.update("UPDATE orders SET reserved_refund_cents=200 WHERE id=?", order);
    fixture.dropRefund.set(true);
    int calls = fixture.refundCount.get();
    jobs.refund();
    assertEquals(0, ledger("REFUND"));
    fixture.refundStates.put("CER" + request, "SUCCESS");
    db.update("UPDATE refund_request SET next_attempt_at=NOW() WHERE id=?", request);
    jobs.refund();
    assertEquals(calls + 1, fixture.refundCount.get());
    assertEquals(1, ledger("REFUND"));
  }

  @Test
  void zeroAmountCheckoutDoesNotCallProvider() {
    checkout = newCheckout(0);
    newOrder(store, 0);
    int calls = fixture.prepayCount.get();
    assertEquals("SUCCEEDED", start().state());
    assertEquals(calls, fixture.prepayCount.get());
    assertEquals("PAID", checkouts.detail(checkout).get("state"));
  }

  @Test
  void zeroAmountRefundCompletesBusinessWithoutCallingProvider() {
    var intent = start();
    callback(intent.id());
    long request =
        db.insert(
            "INSERT INTO"
                + " refund_request(order_id,store_id,amount_cents,state,next_attempt_at,created_at)"
                + " VALUES(?,?,0,'REQUESTED',NOW(),NOW())",
            order,
            store);
    int calls = fixture.refundCount.get();
    assertTrue(jobs.refund());
    assertEquals(calls, fixture.refundCount.get());
    assertEquals(
        "SUCCEEDED", db.one("SELECT state FROM refund_request WHERE id=?", request).get("state"));
  }

  @Test
  void allFourPaymentScenesReturnTheirActualClientActionWithoutPrematurePayment() {
    for (String scene : List.of("MINI_PROGRAM", "JSAPI", "H5", "NATIVE")) {
      checkout = newCheckout(100);
      newOrder(store, 100);
      String app = scene.equals("MINI_PROGRAM") ? "wx-mini-test" : "wx-mp-test";
      db.update(
          "INSERT IGNORE INTO external_identity(user_id,provider,app_id,subject,created_at)"
              + " VALUES(?,'WECHAT',?,?,NOW())",
          user,
          app,
          "openid-" + user + "-" + scene);
      var intent = payments.create(checkout, "scene_" + checkout, scene, 0, "127.0.0.1");
      assertEquals(
          switch (scene) {
            case "MINI_PROGRAM" -> "requestPayment";
            case "JSAPI" -> "wechatBridge";
            case "H5" -> "redirect";
            default -> "qrCode";
          },
          intent.action().get("type"));
      assertEquals("PENDING", checkouts.detail(checkout).get("state"));
    }
  }

  @Test
  void verifiedBillsUseFrozenMerchantsAndRefundAcceptanceDayWithoutPretendingBankArrival()
      throws Exception {
    var intent = start();
    String yesterday = OffsetDateTime.now().minusDays(1).toString();
    fixture.paymentTimes.put(intent.id(), yesterday);
    callback(intent.id());
    long request =
        db.insert(
            "INSERT INTO"
                + " refund_request(order_id,store_id,amount_cents,state,next_attempt_at,created_at)"
                + " VALUES(?,?,200,'REQUESTED',NOW(),NOW())",
            order,
            store);
    fixture.refundTimes.put("CER" + request, yesterday);
    db.update("UPDATE orders SET reserved_refund_cents=200 WHERE id=?", order);
    jobs.refund();
    String original =
        db.one("SELECT sub_mchid FROM payment_channel_order WHERE order_id=?", order)
            .get("sub_mchid")
            .toString();
    String trade =
        db.one("SELECT out_trade_no FROM payment_channel_order WHERE order_id=?", order)
            .get("out_trade_no")
            .toString();
    fixture.bills.put(
        original,
        "商户号,特约商户号,商户订单号,交易状态,商户退款单号,手续费,订单金额,申请退款金额\n"
            + "`1900000109,`"
            + original
            + ",`"
            + trade
            + ",`SUCCESS,`0,`0.07,`10.00,`0.00\n"
            + "`1900000109,`"
            + original
            + ",`"
            + trade
            + ",`REFUND,`CER"
            + request
            + ",`-0.02,`10.00,`2.00\n");
    db.update("UPDATE merchant_payment_config SET sub_mchid='999999999' WHERE store_id=?", store);
    actor("ADMIN", 1);
    finance.reconcile(store, LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1));
    var statement = db.one("SELECT * FROM channel_reconciliation WHERE store_id=?", store);
    assertEquals("MATCHED", statement.get("state"));
    assertEquals(2, Db.integer(statement, "trade_count"));
    assertEquals(5, Db.id(statement, "fee_cents"));
    assertEquals(0, ledger("REFUND"));
  }

  @Test
  void badBillHashStopsBeforeSavingFinancialResults() {
    String merchant = "123456" + store;
    fixture.bills.put(merchant, "商户号,特约商户号,商户订单号,交易状态,商户退款单号,手续费,订单金额,申请退款金额\n");
    fixture.badBillHash.set(true);
    actor("ADMIN", 1);
    assertEquals(
        "BILL_HASH",
        assertThrows(
                BusinessException.class,
                () ->
                    finance.reconcile(
                        store, LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)))
            .code());
    assertEquals(
        0, db.count("SELECT COUNT(*) FROM channel_reconciliation WHERE store_id=?", store));
  }

  @Test
  void configurationAndSignedFixtureConnectivityCannotMasqueradeAsLiveAcceptance() {
    start();
    actor("ADMIN", 1);
    var states = readiness.states();
    assertEquals(4, states.size());
    assertTrue(states.stream().noneMatch(s -> s.status().equals("BUSINESS_VERIFIED")));
    assertEquals(
        "CONNECTIVITY_VERIFIED",
        states.stream()
            .filter(s -> s.provider().equals("WECHAT_PAY"))
            .findFirst()
            .orElseThrow()
            .status());
    assertEquals(
        "LIVE_EVIDENCE_REQUIRED",
        assertThrows(
                BusinessException.class,
                () -> readiness.accept("WECHAT_PAY", "fixture-is-not-live-evidence"))
            .code());
  }

  @Test
  void changedMerchantCannotAlterOriginalRefund() {
    var intent = start();
    callback(intent.id());
    String original =
        db.one("SELECT sub_mchid FROM payment_channel_order WHERE order_id=?", order)
            .get("sub_mchid")
            .toString();
    db.update("UPDATE merchant_payment_config SET sub_mchid='999999999' WHERE store_id=?", store);
    long request =
        db.insert(
            "INSERT INTO"
                + " refund_request(order_id,store_id,amount_cents,state,next_attempt_at,created_at)"
                + " VALUES(?,?,100,'REQUESTED',NOW(),NOW())",
            order,
            store);
    db.update("UPDATE orders SET reserved_refund_cents=100 WHERE id=?", order);
    jobs.refund();
    assertEquals(
        original,
        db.one("SELECT sub_mchid FROM channel_refund WHERE request_id=?", request)
            .get("sub_mchid"));
  }

  static final class Fixture {
    static final String PUBLIC_ID = "PUB_KEY_ID_FIXTURE", AES = "01234567890123456789012345678901";
    final HttpServer server;
    final String base, privateKeyPath, publicKeyPath;
    final KeyPair merchant, wechat;
    final Json json = new Json(new tools.jackson.databind.ObjectMapper());
    final Map<String, Map<String, Object>> prepayments = new ConcurrentHashMap<>(),
        refunds = new ConcurrentHashMap<>();
    final Map<String, String> states = new ConcurrentHashMap<>(),
        refundStates = new ConcurrentHashMap<>();
    final Map<String, String> paymentTimes = new ConcurrentHashMap<>(),
        refundTimes = new ConcurrentHashMap<>(),
        bills = new ConcurrentHashMap<>();
    final AtomicBoolean badBillHash = new AtomicBoolean();
    final AtomicReference<String> lastFailure = new AtomicReference<>("");
    final AtomicInteger prepayCount = new AtomicInteger(),
        refundCount = new AtomicInteger(),
        verified = new AtomicInteger();
    final AtomicBoolean dropPrepay = new AtomicBoolean(),
        dropRefund = new AtomicBoolean(),
        failClose = new AtomicBoolean();

    Fixture() {
      try {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        merchant = generator.generateKeyPair();
        wechat = generator.generateKeyPair();
        var directory =
            Files.createDirectories(Path.of("target", "payment-fixture-" + UUID.randomUUID()));
        var privateFile = directory.resolve("merchant.pem");
        var publicFile = directory.resolve("wechat.pem");
        Files.writeString(privateFile, pem("PRIVATE KEY", merchant.getPrivate().getEncoded()));
        Files.writeString(publicFile, pem("PUBLIC KEY", wechat.getPublic().getEncoded()));
        privateKeyPath = privateFile.toString();
        publicKeyPath = publicFile.toString();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(
            Executors.newCachedThreadPool(
                r -> {
                  var t = new Thread(r);
                  t.setDaemon(true);
                  return t;
                }));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    void handle(HttpExchange x) throws IOException {
      try {
        String body = new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
            path = x.getRequestURI().getPath();
        assertRequest(x, body);
        Map<String, Object> data = body.isEmpty() ? Map.of() : json.map(body);
        if (path.equals("/v3/bill/tradebill")) {
          String sub =
              Arrays.stream(x.getRequestURI().getQuery().split("&"))
                  .filter(q -> q.startsWith("sub_mchid="))
                  .findFirst()
                  .orElseThrow()
                  .substring(10);
          if (!bills.containsKey(sub)) {
            send(x, 404, Map.of("code", "NO_BILL_EXIST", "message", "no bill"));
            return;
          }
          String hash =
              java.util.HexFormat.of()
                  .formatHex(
                      MessageDigest.getInstance("SHA-1")
                          .digest(bills.get(sub).getBytes(StandardCharsets.UTF_8)));
          send(
              x,
              200,
              Map.of(
                  "hash_type",
                  "SHA1",
                  "hash_value",
                  badBillHash.get() ? "0".repeat(40) : hash,
                  "download_url",
                  "https://api.mch.weixin.qq.com/v3/billdownload/file?token=" + sub));
          return;
        }
        if (path.equals("/v3/billdownload/file")) {
          byte[] bytes =
              bills.get(x.getRequestURI().getQuery().substring(6)).getBytes(StandardCharsets.UTF_8);
          x.getResponseHeaders().set("Content-Type", "text/plain");
          x.sendResponseHeaders(200, bytes.length);
          x.getResponseBody().write(bytes);
          x.close();
          return;
        }
        if (path.matches("/v3/combine-transactions/(jsapi|h5|native)")) {
          prepayCount.incrementAndGet();
          String id = data.get("combine_out_trade_no").toString();
          prepayments.putIfAbsent(id, data);
          states.putIfAbsent(id, "NOTPAY");
          if (dropPrepay.compareAndSet(true, false)) {
            x.close();
            return;
          }
          send(
              x,
              200,
              Map.of(
                  "code_url",
                  "weixin://wxpay/" + id,
                  "h5_url",
                  "https://wx.tenpay.com/test/" + id,
                  "prepay_id",
                  "prepay_" + id));
          return;
        }
        if (path.contains("/combine-transactions/out-trade-no/")) {
          String[] segments = path.split("/");
          String id = segments[4];
          if (path.endsWith("/close")) {
            if (failClose.get()) {
              send(x, 500, Map.of("code", "SYSTEM_ERROR", "message", "fixture failure"));
              return;
            }
            states.put(id, "CLOSED");
            send(x, 204, Map.of());
            return;
          }
          if (!prepayments.containsKey(id)) {
            send(x, 404, Map.of("code", "ORDER_NOT_EXIST", "message", "not found"));
            return;
          }
          send(x, 200, transaction(id));
          return;
        }
        if (path.equals("/v3/refund/domestic/refunds")) {
          refundCount.incrementAndGet();
          String id = data.get("out_refund_no").toString();
          refunds.putIfAbsent(id, data);
          refundStates.putIfAbsent(id, "PROCESSING");
          if (dropRefund.compareAndSet(true, false)) {
            x.close();
            return;
          }
          send(x, 200, refund(id));
          return;
        }
        if (path.startsWith("/v3/refund/domestic/refunds/")) {
          String id = path.substring(path.lastIndexOf('/') + 1);
          if (!refunds.containsKey(id)) {
            send(x, 404, Map.of("code", "RESOURCE_NOT_EXISTS", "message", "not found"));
            return;
          }
          send(x, 200, refund(id));
          return;
        }
        send(x, 404, Map.of("code", "NOT_FOUND", "message", path));
      } catch (Exception e) {
        send(x, 500, Map.of("code", "FIXTURE_ERROR", "message", e.getClass().getSimpleName()));
      }
    }

    void assertRequest(HttpExchange x, String body) throws Exception {
      String auth = x.getRequestHeaders().getFirst("Authorization");
      var matcher = java.util.regex.Pattern.compile("([a-z_]+)=\"([^\"]+)\"").matcher(auth);
      var fields = new HashMap<String, String>();
      while (matcher.find()) fields.put(matcher.group(1), matcher.group(2));
      String message =
          x.getRequestMethod()
              + "\n"
              + x.getRequestURI()
              + "\n"
              + fields.get("timestamp")
              + "\n"
              + fields.get("nonce_str")
              + "\n"
              + body
              + "\n";
      var verify = Signature.getInstance("SHA256withRSA");
      verify.initVerify(merchant.getPublic());
      verify.update(message.getBytes(StandardCharsets.UTF_8));
      if (!verify.verify(Base64.getDecoder().decode(fields.get("signature"))))
        throw new SecurityException("invalid merchant signature");
      verified.incrementAndGet();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> transaction(String id) {
      var original = prepayments.get(id);
      var result = new LinkedHashMap<String, Object>();
      result.put("combine_appid", original.get("combine_appid"));
      result.put("combine_mchid", original.get("combine_mchid"));
      result.put("combine_out_trade_no", id);
      var orders = new ArrayList<Map<String, Object>>();
      for (var part : (List<Map<String, Object>>) original.get("sub_orders")) {
        var p = new LinkedHashMap<String, Object>(part);
        p.put("trade_state", states.get(id));
        p.put("transaction_id", "WX" + part.get("out_trade_no"));
        p.put("success_time", paymentTimes.getOrDefault(id, OffsetDateTime.now().toString()));
        orders.add(p);
      }
      result.put("sub_orders", orders);
      return result;
    }

    Map<String, Object> paid(String id) {
      states.put(id, "SUCCESS");
      return transaction(id);
    }

    Map<String, Object> refund(String id) {
      var data = new LinkedHashMap<String, Object>(refunds.get(id));
      data.put("refund_id", "WX" + id);
      data.put("status", refundStates.get(id));
      data.put("success_time", OffsetDateTime.now().toString());
      data.put("create_time", refundTimes.getOrDefault(id, OffsetDateTime.now().toString()));
      return data;
    }

    record Notice(String event, String timestamp, String nonce, String signature, String body) {}

    Notice notice(Map<String, Object> data, String type) {
      try {
        String nonce = "012345678901", associated = "transaction";
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.ENCRYPT_MODE,
            new SecretKeySpec(AES.getBytes(StandardCharsets.UTF_8), "AES"),
            new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
        String event = UUID.randomUUID().toString(),
            body =
                json.write(
                    Map.of(
                        "id",
                        event,
                        "event_type",
                        type,
                        "resource",
                        Map.of(
                            "algorithm",
                            "AEAD_AES_256_GCM",
                            "associated_data",
                            associated,
                            "nonce",
                            nonce,
                            "ciphertext",
                            Base64.getEncoder()
                                .encodeToString(
                                    cipher.doFinal(
                                        json.write(data).getBytes(StandardCharsets.UTF_8))))));
        String timestamp = Long.toString(Instant.now().getEpochSecond()),
            requestNonce = UUID.randomUUID().toString();
        return new Notice(
            event,
            timestamp,
            requestNonce,
            sign(timestamp + "\n" + requestNonce + "\n" + body + "\n"),
            body);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    String sign(String message) {
      try {
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(wechat.getPrivate());
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }

    void send(HttpExchange x, int status, Map<String, Object> data) throws IOException {
      byte[] bytes =
          status == 204 ? new byte[0] : json.write(data).getBytes(StandardCharsets.UTF_8);
      String timestamp = Long.toString(Instant.now().getEpochSecond()),
          nonce = UUID.randomUUID().toString();
      x.getResponseHeaders().set("Content-Type", "application/json");
      x.getResponseHeaders().set("Wechatpay-Serial", PUBLIC_ID);
      x.getResponseHeaders().set("Wechatpay-Timestamp", timestamp);
      x.getResponseHeaders().set("Wechatpay-Nonce", nonce);
      x.getResponseHeaders()
          .set(
              "Wechatpay-Signature",
              sign(
                  timestamp
                      + "\n"
                      + nonce
                      + "\n"
                      + new String(bytes, StandardCharsets.UTF_8)
                      + "\n"));
      x.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
      if (status != 204) x.getResponseBody().write(bytes);
      x.close();
    }

    static String pem(String name, byte[] data) {
      return "-----BEGIN "
          + name
          + "-----\n"
          + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(data)
          + "\n-----END "
          + name
          + "-----\n";
    }
  }
}
