package com.campuseats.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.campuseats.common.BusinessException;
import com.campuseats.infra.*;
import com.campuseats.security.*;
import com.sun.net.httpserver.HttpServer;
import com.tencentcloudapi.common.profile.HttpProfile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(
    properties = {
      "spring.profiles.active=demo",
      "campus.jwt-secret=identity-test-secret-with-at-least-thirty-two-bytes",
      "campus.demo-admin-password=IdentityTest!2026",
      "campus.tasks-enabled=false",
      "campus.identity.phone-enabled=true",
      "campus.notifications.sms.secret-id=test-id",
      "campus.notifications.sms.secret-key=test-key",
      "campus.notifications.sms.app-id=1400000000",
      "campus.notifications.sms.sign-name=测试",
      "campus.notifications.sms.otp-template-id=otp-template",
      "campus.identity.policy-version=test-v1",
      "campus.wechat-appid=mini-app",
      "campus.wechat-secret=mini-secret",
      "campus.identity.oauth-appid=official-app",
      "campus.identity.oauth-secret=official-secret",
      "campus.identity.oauth-redirect-uri=https://example.test/api/v1/user/auth/wechat/oauth/callback",
      "campus.notifications.enabled=true",
      "campus.notifications.wechat.templates.ORDER_READY=ready-template",
      "campus.notifications.wechat.fields.ORDER_READY={\"thing1\":\"summary\",\"character_string2\":\"orderNumber\"}"
    })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IdentityNotificationIntegrationTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("identity_test")
          .withUsername("test")
          .withPassword("test")
          .withCreateContainerCmdModifier(c -> c.getHostConfig().withMemory(768L * 1024 * 1024));

  @Container
  static final GenericContainer<?> redisContainer =
      new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

  static final HttpServer server;
  static final AtomicReference<String> smsBody = new AtomicReference<>(),
      smsAuthorization = new AtomicReference<>();
  static final AtomicInteger smsRequests = new AtomicInteger(), wechatSends = new AtomicInteger();
  static volatile String nextOpenid = "mini-subject";
  static volatile int subscriptionError = 0;
  static volatile String smsCode = "Ok";
  static volatile boolean disconnectSms = false;
  static volatile boolean malformedSubscriptionResponse = false;
  static volatile CountDownLatch exchangeStarted, continueExchange;

  static {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/",
          exchange -> {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            String response;
            if (path.startsWith("/sns/")) {
              if (exchangeStarted != null) {
                exchangeStarted.countDown();
                try {
                  if (!continueExchange.await(10, TimeUnit.SECONDS))
                    throw new IOException("Exchange gate timed out");
                } catch (InterruptedException ex) {
                  Thread.currentThread().interrupt();
                  throw new IOException(ex);
                }
              }
              response = "{\"openid\":\"" + nextOpenid + "\"}";
            } else if (path.equals("/cgi-bin/stable_token"))
              response = "{\"access_token\":\"fixture-token\",\"expires_in\":7200}";
            else if (path.equals("/cgi-bin/message/subscribe/send")) {
              wechatSends.incrementAndGet();
              response =
                  malformedSubscriptionResponse ? "{}" : "{\"errcode\":" + subscriptionError + "}";
            } else {
              smsRequests.incrementAndGet();
              smsBody.set(body);
              smsAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
              if (disconnectSms) {
                exchange.close();
                return;
              }
              String sentPhone =
                  new tools.jackson.databind.ObjectMapper()
                      .readTree(body)
                      .get("PhoneNumberSet")
                      .get(0)
                      .asText();
              response =
                  "{\"Response\":{\"SendStatusSet\":[{\"Code\":\""
                      + smsCode
                      + "\",\"PhoneNumber\":\""
                      + sentPhone
                      + "\",\"SerialNo\":\"fixture-serial\"}],\"RequestId\":\"fixture-request\"}}";
            }
            byte[] data = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, data.length);
            try (var output = exchange.getResponseBody()) {
              output.write(data);
            }
          });
      server.start();
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", mysql::getJdbcUrl);
    p.add("spring.datasource.username", mysql::getUsername);
    p.add("spring.datasource.password", mysql::getPassword);
    p.add("spring.data.redis.host", redisContainer::getHost);
    p.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    RacingDb racingDb(JdbcTemplate jdbc) {
      return new RacingDb(jdbc);
    }

    @Bean
    @Primary
    TencentSmsGateway fixtureSms(Environment environment) {
      var profile = new HttpProfile();
      profile.setEndpoint("127.0.0.1:" + server.getAddress().getPort());
      profile.setProtocol(HttpProfile.REQ_HTTP);
      return new TencentSmsGateway(environment, profile);
    }

    @Bean
    @Primary
    RestClient fixtureWechat() {
      var factory = new JdkClientHttpRequestFactory();
      return RestClient.builder()
          .requestFactory(
              (uri, method) ->
                  factory.createRequest(
                      URI.create(
                          "http://127.0.0.1:"
                              + server.getAddress().getPort()
                              + uri.getRawPath()
                              + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())),
                      method))
          .build();
    }
  }

  /** Gates only the real missing-row lookup so both transactions reach the unique-key race. */
  static class RacingDb extends Db {
    volatile String raceSql, raceSubject;
    volatile CyclicBarrier barrier;

    RacingDb(JdbcTemplate jdbc) {
      super(jdbc);
    }

    @Override
    public Map<String, Object> one(String sql, Object... args) {
      var result = super.one(sql, args);
      if (result == null
          && barrier != null
          && sql.startsWith(raceSql)
          && Arrays.asList(args).contains(raceSubject)) {
        try {
          barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(ex);
        } catch (BrokenBarrierException | TimeoutException ex) {
          throw new IllegalStateException(ex);
        }
      }
      return result;
    }

    void race(String sql, String subject) {
      raceSql = sql;
      raceSubject = subject;
      barrier = new CyclicBarrier(2);
    }
  }

  @Autowired UserIdentityService identities;
  @Autowired PhoneChallenges challenges;
  @Autowired ExternalNotifications notifications;
  @Autowired AuthService auth;
  @Autowired Db db;
  @Autowired Json json;
  @Autowired StringRedisTemplate redis;
  @Autowired RacingDb racingDb;
  static final AtomicInteger phoneSequence = new AtomicInteger();

  private void actor(long user) {
    actor(auth.authenticate(auth.issue("USER", user).get("token").toString()));
  }

  private static void actor(Actor principal) {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
  }

  @BeforeEach
  void isolatedQueue() {
    db.update("DELETE FROM notification_delivery");
    smsCode = "Ok";
    subscriptionError = 0;
    disconnectSms = false;
    malformedSubscriptionResponse = false;
    redis.delete(
        List.of(
            "campus:wechat:access-token:mini-app", "campus:wechat:access-token:mini-app:refresh"));
    racingDb.barrier = null;
    exchangeStarted = null;
    continueExchange = null;
  }

  @AfterEach
  void clear() {
    racingDb.barrier = null;
    if (continueExchange != null) continueExchange.countDown();
    exchangeStarted = null;
    SecurityContextHolder.clearContext();
  }

  @AfterAll
  static void stop() {
    server.stop(0);
  }

  private String phone() {
    return "138" + String.format(Locale.ROOT, "%08d", phoneSequence.incrementAndGet());
  }

  private String sentCode() {
    return ((List<?>) json.map(smsBody.get()).get("TemplateParamSet")).getFirst().toString();
  }

  private long login(String phone) {
    challenges.send(phone, "LOGIN", 0, "fixture-ip");
    return Long.parseLong(identities.phoneLogin(phone, sentCode(), "test-v1").get("id").toString());
  }

  private long order(long user, int status) {
    return db.insert(
        "INSERT INTO orders(number,user_id,address_book_id,order_time,amount,status,completed_at)"
            + " VALUES(?,?,0,?,10,?,?)",
        "fixture-" + UUID.randomUUID().toString().substring(0, 8),
        user,
        LocalDateTime.now(),
        status,
        LocalDateTime.now());
  }

  @Test
  void realSdkSignsSmsAndChallengesAreSingleUsePurposeBoundAndRateLimited() {
    String phone = phone();
    challenges.send(phone, "LOGIN", 0, "fixture-ip");
    String code = sentCode();
    assertTrue(smsAuthorization.get().startsWith("TC3-HMAC-SHA256"));
    assertEquals(List.of("+86" + phone), json.map(smsBody.get()).get("PhoneNumberSet"));
    assertThrows(BusinessException.class, () -> challenges.send(phone, "LOGIN", 0, "fixture-ip"));
    assertThrows(BusinessException.class, () -> challenges.consume(phone, "BIND", 1, code));
    challenges.consume(phone, "LOGIN", 0, code);
    assertEquals(
        "OTP_INVALID",
        assertThrows(BusinessException.class, () -> challenges.consume(phone, "LOGIN", 0, code))
            .code());
    String another = phone();
    challenges.send(another, "LOGIN", 0, "fixture-ip");
    String right = sentCode();
    for (int i = 0; i < 5; i++)
      assertThrows(
          BusinessException.class,
          () ->
              challenges.consume(
                  another, "LOGIN", 0, "999999".equals(right) ? "000000" : "999999"));
    assertThrows(BusinessException.class, () -> challenges.consume(another, "LOGIN", 0, right));
  }

  @Test
  void accountQuotaCoversDifferentPhonesAndPurposesButIsolatesAnotherAuthenticatedAccount() {
    String ownPhone = phone();
    long user = login(ownPhone);
    actor(user);
    int sends = smsRequests.get();
    for (int i = 0; i < 10; i++) identities.sendCode(phone(), "BIND", "account-fixture-ip-" + i);
    String blockedPhone = phone();
    assertEquals(
        "ACCOUNT_RATE_LIMITED",
        assertThrows(
                BusinessException.class,
                () -> identities.sendCode(blockedPhone, "BIND", "another-ip"))
            .code());
    redis.delete("campus:otp:cooldown:" + com.campuseats.common.Hashes.sha256(ownPhone));
    assertEquals(
        "ACCOUNT_RATE_LIMITED",
        assertThrows(
                BusinessException.class, () -> identities.sendCode(ownPhone, "CLOSE", "close-ip"))
            .code());
    assertEquals(sends + 10, smsRequests.get());
    long other =
        db.insert("INSERT INTO user(name,create_time) VALUES('独立额度',?)", LocalDateTime.now());
    actor(other);
    identities.sendCode(blockedPhone, "BIND", "independent-ip");
    assertEquals(sends + 11, smsRequests.get());
    assertEquals("1", redis.opsForValue().get("campus:otp:account:" + other));
    assertTrue(redis.getExpire("campus:otp:account:" + user) > 0);
    assertTrue(redis.getExpire("campus:otp:account:" + user) <= 86400);
  }

  @Test
  void phoneLoginRequiresCurrentConsentAndNeverMergesAnotherAccount() {
    String phone = phone();
    challenges.send(phone, "LOGIN", 0, "fixture-ip");
    String code = sentCode();
    assertEquals(
        "CONSENT_REQUIRED",
        assertThrows(
                BusinessException.class, () -> identities.phoneLogin(phone, code, "old-policy"))
            .code());
    var result = identities.phoneLogin(phone, code, "test-v1");
    long owner = Long.parseLong(result.get("id").toString());
    assertEquals(
        owner, Db.id(db.one("SELECT user_id FROM verified_phone WHERE phone=?", phone), "user_id"));
    long other =
        db.insert("INSERT INTO user(name,create_time) VALUES('另一个人',?)", LocalDateTime.now());
    actor(other);
    redis.delete("campus:otp:cooldown:" + com.campuseats.common.Hashes.sha256(phone));
    challenges.send(phone, "BIND", other, "fixture-ip");
    String binding = sentCode();
    assertEquals(
        "PHONE_ALREADY_BOUND",
        assertThrows(BusinessException.class, () -> identities.bindPhone(phone, binding)).code());
    assertEquals(
        1,
        db.count(
            "SELECT COUNT(*) FROM privacy_consent WHERE user_id=? AND policy_version='test-v1'",
            owner));
  }

  @Test
  void exactAppIdentityAndOauthStateAreEnforced() {
    nextOpenid = "mini-" + UUID.randomUUID();
    long user = Long.parseLong(identities.miniLogin("mini-code").get("id").toString());
    assertEquals(nextOpenid, identities.openid(user, "mini-app"));
    assertThrows(BusinessException.class, () -> identities.openid(user, "official-app"));
    var session = auth.issue("USER", user);
    Actor principal = auth.authenticate(session.get("token").toString());
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    String url = identities.startOauth().get("url");
    String state = url.substring(url.indexOf("&state=") + 7, url.indexOf("#wechat_redirect"));
    nextOpenid = "official-" + UUID.randomUUID();
    identities.completeOauth(state, "official-code");
    assertEquals(nextOpenid, identities.openid(user, "official-app"));
    assertThrows(BusinessException.class, () -> identities.completeOauth(state, "replay"));
  }

  @Test
  void externallyIssuedSubjectsRemainCaseSensitive() {
    String suffix = UUID.randomUUID().toString();
    nextOpenid = "A" + suffix;
    long upper = Long.parseLong(identities.miniLogin("code-A").get("id").toString());
    nextOpenid = "a" + suffix;
    long lower = Long.parseLong(identities.miniLogin("code-a").get("id").toString());
    assertNotEquals(upper, lower);
    assertNotEquals(identities.openid(upper, "mini-app"), identities.openid(lower, "mini-app"));
  }

  @Test
  void revokedSessionCannotMutateProfileBindIdentityConsentSubscribeOrClose() {
    String phone = phone();
    long user = login(phone);
    actor(user);
    Actor principal = Actor.user();
    String otherPhone = phone();
    challenges.send(otherPhone, "BIND", user, "fixture-ip");
    String binding = sentCode();
    redis.delete("campus:otp:cooldown:" + com.campuseats.common.Hashes.sha256(phone));
    challenges.send(phone, "CLOSE", user, "fixture-ip");
    String closing = sentCode();
    String originalName = db.one("SELECT name FROM user WHERE id=?", user).get("name").toString();
    auth.logout(principal);
    nextOpenid = "revoked-" + UUID.randomUUID();
    for (Runnable action :
        List.<Runnable>of(
            () -> identities.updateName("过期会话不应生效"),
            () -> identities.bindPhone(otherPhone, binding),
            () -> identities.bindMiniProgram("stale-code"),
            () -> identities.consent("test-v1"),
            () -> notifications.authorize("ready-template", true),
            () -> identities.close(phone, closing))) {
      assertEquals("UNAUTHORIZED", assertThrows(BusinessException.class, action::run).code());
    }
    assertEquals(originalName, db.one("SELECT name FROM user WHERE id=?", user).get("name"));
    assertEquals(1, db.count("SELECT status FROM user WHERE id=?", user));
    assertEquals(0, db.count("SELECT COUNT(*) FROM external_identity WHERE user_id=?", user));
    assertEquals(0, db.count("SELECT COUNT(*) FROM verified_phone WHERE phone=?", otherPhone));
    assertEquals(0, db.count("SELECT COUNT(*) FROM account_closure WHERE user_id=?", user));
    assertEquals(
        0, db.count("SELECT COUNT(*) FROM notification_subscription WHERE user_id=?", user));
  }

  @Test
  void changedAuthenticationVersionCannotMutateUsingPreviouslyAuthenticatedActor() {
    long user = db.insert("INSERT INTO user(name,create_time) VALUES('原名',?)", LocalDateTime.now());
    actor(user);
    db.update("UPDATE user SET auth_version=auth_version+1 WHERE id=?", user);
    assertEquals(
        "UNAUTHORIZED",
        assertThrows(BusinessException.class, () -> identities.updateName("新名")).code());
    assertEquals("原名", db.one("SELECT name FROM user WHERE id=?", user).get("name"));
  }

  @Test
  void oauthRevokedDuringTheExchangeCannotBindAfterTheResponse() throws Exception {
    long user =
        db.insert("INSERT INTO user(name,create_time) VALUES('授权测试',?)", LocalDateTime.now());
    actor(user);
    Actor principal = Actor.user();
    String url = identities.startOauth().get("url");
    String state = url.substring(url.indexOf("&state=") + 7, url.indexOf("#wechat_redirect"));
    nextOpenid = "oauth-revoked-" + UUID.randomUUID();
    exchangeStarted = new CountDownLatch(1);
    continueExchange = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var result =
          executor.submit(
              () ->
                  assertThrows(
                      BusinessException.class, () -> identities.completeOauth(state, "code")));
      assertTrue(exchangeStarted.await(10, TimeUnit.SECONDS));
      auth.logout(principal);
      continueExchange.countDown();
      assertEquals("UNAUTHORIZED", result.get(10, TimeUnit.SECONDS).code());
    }
    assertEquals(0, db.count("SELECT COUNT(*) FROM external_identity WHERE user_id=?", user));
  }

  @Test
  void concurrentWechatBindingsHaveOneWinnerAndExplicitOwnershipConflict() throws Exception {
    long first =
        db.insert("INSERT INTO user(name,create_time) VALUES('绑定甲',?)", LocalDateTime.now());
    long second =
        db.insert("INSERT INTO user(name,create_time) VALUES('绑定乙',?)", LocalDateTime.now());
    Actor a = auth.authenticate(auth.issue("USER", first).get("token").toString());
    Actor b = auth.authenticate(auth.issue("USER", second).get("token").toString());
    nextOpenid = "racing-" + UUID.randomUUID();
    racingDb.race("SELECT user_id,subject FROM external_identity", nextOpenid);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var left =
          executor.submit(() -> bindingResult(a, () -> identities.bindMiniProgram("race-A")));
      var right =
          executor.submit(() -> bindingResult(b, () -> identities.bindMiniProgram("race-B")));
      assertEquals(
          Set.of("OK", "WECHAT_ALREADY_BOUND"),
          Set.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS)));
    }
    assertEquals(
        1,
        db.count(
            "SELECT COUNT(*) FROM external_identity WHERE app_id='mini-app' AND subject=?",
            nextOpenid));
    long winner =
        Db.id(
            db.one(
                "SELECT user_id FROM external_identity WHERE app_id='mini-app' AND subject=?",
                nextOpenid),
            "user_id");
    actor(winner);
    identities.bindMiniProgram("same-binding-is-idempotent");
  }

  @Test
  void concurrentPhoneBindingsCannotClaimAnotherUsersVerifiedNumber() throws Exception {
    String phone = phone();
    long first =
        db.insert("INSERT INTO user(name,create_time) VALUES('手机甲',?)", LocalDateTime.now());
    long second =
        db.insert("INSERT INTO user(name,create_time) VALUES('手机乙',?)", LocalDateTime.now());
    Actor a = auth.authenticate(auth.issue("USER", first).get("token").toString());
    Actor b = auth.authenticate(auth.issue("USER", second).get("token").toString());
    challenges.send(phone, "BIND", first, "fixture-ip");
    String firstCode = sentCode();
    redis.delete("campus:otp:cooldown:" + com.campuseats.common.Hashes.sha256(phone));
    challenges.send(phone, "BIND", second, "fixture-ip");
    String secondCode = sentCode();
    racingDb.race("SELECT user_id FROM verified_phone WHERE phone=? FOR UPDATE", phone);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var left =
          executor.submit(() -> bindingResult(a, () -> identities.bindPhone(phone, firstCode)));
      var right =
          executor.submit(() -> bindingResult(b, () -> identities.bindPhone(phone, secondCode)));
      assertEquals(
          Set.of("OK", "PHONE_ALREADY_BOUND"),
          Set.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS)));
    }
    long winner =
        Db.id(db.one("SELECT user_id FROM verified_phone WHERE phone=?", phone), "user_id");
    long loser = winner == first ? second : first;
    assertEquals(phone, db.one("SELECT phone FROM user WHERE id=?", winner).get("phone"));
    assertNull(db.one("SELECT phone FROM user WHERE id=?", loser).get("phone"));
  }

  private static String bindingResult(Actor principal, Runnable action) {
    actor(principal);
    try {
      action.run();
      return "OK";
    } catch (BusinessException ex) {
      return ex.code();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void rejectedSmsDoesNotRetryTheChargeableSdkCall() {
    smsCode = "LimitExceeded.PhoneNumberDailyLimit";
    int before = smsRequests.get();
    assertEquals(
        "SMS_REJECTED",
        assertThrows(
                BusinessException.class, () -> challenges.send(phone(), "LOGIN", 0, "fixture-ip"))
            .code());
    assertEquals(before + 1, smsRequests.get());
  }

  @Test
  void lostSmsResponseDoesNotReplayTheSdkHttpRequest() {
    disconnectSms = true;
    int before = smsRequests.get();
    assertEquals(
        "SMS_RESULT_UNKNOWN",
        assertThrows(
                BusinessException.class, () -> challenges.send(phone(), "LOGIN", 0, "fixture-ip"))
            .code());
    assertEquals(before + 1, smsRequests.get());
  }

  @Test
  void closureBlocksActiveWorkThenRevokesSessionsAndRemovesIdentifiers() {
    String phone = phone();
    long user = login(phone);
    actor(user);
    long order = order(user, 3);
    var session = auth.issue("USER", user);
    String token = session.get("token").toString();
    redis.delete("campus:otp:cooldown:" + com.campuseats.common.Hashes.sha256(phone));
    challenges.send(phone, "CLOSE", user, "fixture-ip");
    String code = sentCode();
    assertEquals(
        "ACCOUNT_HAS_ACTIVE_BUSINESS",
        assertThrows(BusinessException.class, () -> identities.close(phone, code)).code());
    db.update("UPDATE orders SET status=5 WHERE id=?", order);
    redis.delete("campus:otp:cooldown:" + com.campuseats.common.Hashes.sha256(phone));
    challenges.send(phone, "CLOSE", user, "fixture-ip");
    identities.close(phone, sentCode());
    assertEquals(0, db.count("SELECT COUNT(*) FROM verified_phone WHERE user_id=?", user));
    assertThrows(BusinessException.class, () -> auth.authenticate(token));
    assertEquals(
        "COMPLETED",
        db.one("SELECT state FROM account_closure WHERE user_id=?", user).get("state"));
    assertEquals(1, db.count("SELECT COUNT(*) FROM orders WHERE id=?", order));
  }

  @Test
  void notificationsAreDurableDeduplicatedAndRequireProviderAuthorization() {
    nextOpenid = "notify-" + UUID.randomUUID();
    long user = Long.parseLong(identities.miniLogin("notify").get("id").toString());
    actor(user);
    long order = order(user, 3);
    long event =
        db.insert(
            "INSERT INTO outbox_event(event_type,aggregate_id,payload,next_attempt_at,created_at)"
                + " VALUES('ORDER_READY',?,'{}',?,?)",
            order,
            LocalDateTime.now(),
            LocalDateTime.now());
    notifications.enqueue(event, order, "ORDER_READY", "餐品已备好");
    notifications.enqueue(event, order, "ORDER_READY", "餐品已备好");
    assertEquals(1, db.count("SELECT COUNT(*) FROM notification_delivery WHERE event_id=?", event));
    db.update(
        "UPDATE notification_delivery SET next_attempt_at=? WHERE event_id=?",
        LocalDateTime.now().minusSeconds(2),
        event);
    assertTrue(notifications.deliver());
    assertEquals(
        "SKIPPED",
        db.one("SELECT state FROM notification_delivery WHERE event_id=?", event).get("state"));
    notifications.authorize("ready-template", true);
    db.update(
        "UPDATE notification_delivery SET state='REQUESTED',next_attempt_at=? WHERE event_id=?",
        LocalDateTime.now().minusSeconds(2),
        event);
    int before = wechatSends.get();
    assertTrue(notifications.deliver());
    assertEquals(before + 1, wechatSends.get());
    assertEquals(
        "ACCEPTED",
        db.one("SELECT state FROM notification_delivery WHERE event_id=?", event).get("state"));
    assertFalse(notifications.deliver());
    assertEquals(before + 1, wechatSends.get());
    assertEquals(
        0, db.count("SELECT credits FROM notification_subscription WHERE user_id=?", user));
  }

  @Test
  void abandonedNotificationSendIsUnknownAndNeverAutomaticallyResent() {
    long user =
        db.insert("INSERT INTO user(name,create_time) VALUES('通知测试',?)", LocalDateTime.now());
    db.update(
        "INSERT INTO"
            + " notification_delivery(event_id,user_id,order_id,channel,template_id,state,summary,lease_token,lease_until,next_attempt_at,created_at,updated_at)"
            + " VALUES(999999,?,0,'WECHAT','ready-template','PROCESSING','test','old',?,?,?,?)",
        user,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now(),
        LocalDateTime.now(),
        LocalDateTime.now());
    int before = wechatSends.get();
    assertFalse(notifications.deliver());
    assertEquals(before, wechatSends.get());
    assertEquals(
        "UNKNOWN",
        db.one("SELECT state FROM notification_delivery WHERE event_id=999999").get("state"));
  }

  @Test
  void sharedTokenRefreshLockDefersBeforeConsumingCreditThenSendsExactlyOnce() {
    long event = subscriptionTask();
    var task = db.one("SELECT * FROM notification_delivery WHERE event_id=?", event);
    String lock = "campus:wechat:access-token:mini-app:refresh";
    redis.opsForValue().set(lock, "other-instance", Duration.ofSeconds(30));
    int sends = wechatSends.get();
    assertTrue(notifications.deliver());
    var deferred = db.one("SELECT * FROM notification_delivery WHERE event_id=?", event);
    assertEquals("REQUESTED", deferred.get("state"));
    assertEquals("WECHAT_TOKEN_BUSY", deferred.get("last_error"));
    assertEquals(1, Db.integer(deferred, "attempts"));
    assertTrue(
        Db.time(deferred.get("next_attempt_at")).isAfter(LocalDateTime.now().plusSeconds(7)));
    assertEquals(
        1,
        db.count(
            "SELECT credits FROM notification_subscription WHERE user_id=?", task.get("user_id")));
    assertEquals(sends, wechatSends.get());
    assertFalse(notifications.deliver());
    redis.delete(lock);
    db.update(
        "UPDATE notification_delivery SET next_attempt_at=? WHERE event_id=?",
        LocalDateTime.now().minusSeconds(2),
        event);
    assertTrue(notifications.deliver());
    var sent =
        db.one(
            "SELECT state,attempts,last_error FROM notification_delivery WHERE event_id=?", event);
    assertEquals("ACCEPTED", sent.get("state"));
    assertEquals(2, Db.integer(sent, "attempts"));
    assertNull(sent.get("last_error"));
    assertEquals(
        0,
        db.count(
            "SELECT credits FROM notification_subscription WHERE user_id=?", task.get("user_id")));
    assertEquals(sends + 1, wechatSends.get());
    assertFalse(notifications.deliver());
    assertEquals(sends + 1, wechatSends.get());
    assertEquals(3, db.count("SELECT status FROM orders WHERE id=?", task.get("order_id")));
  }

  @Test
  void sharedTokenRefreshContentionStopsAfterThreeClaimsWithoutSpendingAuthorization() {
    long event = subscriptionTask();
    var task = db.one("SELECT * FROM notification_delivery WHERE event_id=?", event);
    redis
        .opsForValue()
        .set(
            "campus:wechat:access-token:mini-app:refresh",
            "other-instance",
            Duration.ofSeconds(30));
    int sends = wechatSends.get();
    for (int attempt = 1; attempt <= 3; attempt++) {
      db.update(
          "UPDATE notification_delivery SET next_attempt_at=? WHERE event_id=?",
          LocalDateTime.now().minusSeconds(2),
          event);
      assertTrue(notifications.deliver());
      var row =
          db.one(
              "SELECT state,attempts,last_error FROM notification_delivery WHERE event_id=?",
              event);
      assertEquals(attempt < 3 ? "REQUESTED" : "FAILED", row.get("state"));
      assertEquals(attempt, Db.integer(row, "attempts"));
      assertEquals("WECHAT_TOKEN_BUSY", row.get("last_error"));
    }
    redis.delete("campus:wechat:access-token:mini-app:refresh");
    assertFalse(notifications.deliver());
    assertEquals(sends, wechatSends.get());
    assertEquals(
        1,
        db.count(
            "SELECT credits FROM notification_subscription WHERE user_id=?", task.get("user_id")));
  }

  @Test
  void unknownWechatSendNeverUsesTheCredentialContentionRetryPath() {
    long event = subscriptionTask();
    var task = db.one("SELECT * FROM notification_delivery WHERE event_id=?", event);
    malformedSubscriptionResponse = true;
    int sends = wechatSends.get();
    assertTrue(notifications.deliver());
    var row = db.one("SELECT state,last_error FROM notification_delivery WHERE event_id=?", event);
    assertEquals("UNKNOWN", row.get("state"));
    assertEquals("WECHAT_MESSAGE_UNKNOWN", row.get("last_error"));
    assertEquals(
        0,
        db.count(
            "SELECT credits FROM notification_subscription WHERE user_id=?", task.get("user_id")));
    db.update(
        "UPDATE notification_delivery SET next_attempt_at=? WHERE event_id=?",
        LocalDateTime.now().minusSeconds(2),
        event);
    assertFalse(notifications.deliver());
    assertEquals(sends + 1, wechatSends.get());
    assertEquals(3, db.count("SELECT status FROM orders WHERE id=?", task.get("order_id")));
  }

  private long subscriptionTask() {
    nextOpenid = "token-contention-" + UUID.randomUUID();
    long user = Long.parseLong(identities.miniLogin("token-contention-code").get("id").toString());
    actor(user);
    notifications.authorize("ready-template", true);
    long order = order(user, 3);
    long event =
        db.insert(
            "INSERT INTO outbox_event(event_type,aggregate_id,payload,next_attempt_at,created_at)"
                + " VALUES('ORDER_READY',?,'{}',?,?)",
            order,
            LocalDateTime.now(),
            LocalDateTime.now());
    notifications.enqueue(event, order, "ORDER_READY", "餐品已备好");
    db.update(
        "UPDATE notification_delivery SET next_attempt_at=? WHERE event_id=?",
        LocalDateTime.now().minusSeconds(2),
        event);
    return event;
  }
}
