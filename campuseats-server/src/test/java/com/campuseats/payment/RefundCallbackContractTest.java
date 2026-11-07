package com.campuseats.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.campuseats.common.BusinessException;
import com.campuseats.infra.Db;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

/** Uses the service-provider notification shape, which intentionally has no amount.currency. */
@Testcontainers
class RefundCallbackContractTest {
  @Container
  static final MySQLContainer mysql =
      new MySQLContainer("mysql:8.4")
          .withDatabaseName("refund_contract_test")
          .withUsername("test")
          .withPassword("test");

  static final PaymentIntegrationTest.Fixture fixture = new PaymentIntegrationTest.Fixture();
  private Db db;
  private ChannelRefunds refunds;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
        .load()
        .migrate();
  }

  @AfterAll
  static void stop() {
    fixture.server.stop(0);
  }

  @BeforeEach
  void setup() {
    var source =
        new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    db = new Db(new JdbcTemplate(source));
    db.update("DELETE FROM payment_callback");
    db.update("DELETE FROM channel_refund");
    db.update("DELETE FROM refund_request");
    db.update(
        "INSERT INTO"
            + " refund_request(id,order_id,store_id,amount_cents,state,next_attempt_at,created_at)"
            + " VALUES(9001,9001,1,500,'PROCESSING',NOW(),NOW())");
    db.update(
        "INSERT INTO"
            + " channel_refund(request_id,intent_id,sub_mchid,out_trade_no,out_refund_no,amount_cents,total_cents,state,provider_id,submitted,accepted_at,updated_at)"
            + " VALUES(9001,'intent','1900000109','ORDER1','REFUND1',500,1000,'PROCESSING','WXREFUND1',true,NOW(),NOW())");
    var env =
        new MockEnvironment()
            .withProperty("campus.payment.provider", "wechat")
            .withProperty("campus.payment.merchant-id", "1900000100")
            .withProperty("campus.payment.serial", "MERCHANT_SERIAL")
            .withProperty("campus.payment.private-key-path", fixture.privateKeyPath)
            .withProperty("campus.payment.public-key-path", fixture.publicKeyPath)
            .withProperty("campus.payment.public-key-id", PaymentIntegrationTest.Fixture.PUBLIC_ID)
            .withProperty("campus.payment.api-v3-key", PaymentIntegrationTest.Fixture.AES)
            .withProperty("campus.payment.mini-app-id", "wx-mini")
            .withProperty("campus.payment.mp-app-id", "wx-mp")
            .withProperty("campus.payment.notify-base", "https://example.test");
    var settings = new WechatPaySettings(env);
    var config =
        new RSAPublicKeyConfig.Builder()
            .merchantId(settings.merchantId)
            .merchantSerialNumber(settings.serial)
            .privateKeyFromPath(settings.privateKey)
            .publicKeyFromPath(settings.publicKey)
            .publicKeyId(settings.publicKeyId)
            .apiV3Key(settings.apiV3Key)
            .build();
    var channel =
        new WechatPayChannel(
            settings,
            fixture.json,
            Clock.systemUTC(),
            config,
            new DefaultHttpClientBuilder().config(config).disableRetryOnConnectionFailure().build(),
            fixture.base);
    refunds =
        new ChannelRefunds(
            db,
            fixture.json,
            Clock.systemUTC(),
            channel,
            settings,
            new DataSourceTransactionManager(source));
  }

  @Test
  void acceptsAndDeduplicatesSignedServiceProviderSuccessWithoutCurrency() {
    var notice = fixture.notice(notification("SUCCESS"), "REFUND.SUCCESS");
    deliver(notice);
    deliver(notice);
    assertEquals("SUCCESS", channelState());
    assertEquals("REQUESTED", taskState());
    assertEquals(1, db.count("SELECT COUNT(*) FROM payment_callback"));
  }

  @Test
  void persistsClosedAndAbnormalNotificationsWithoutRollingBackTheReceipt() {
    deliver(fixture.notice(notification("ABNORMAL"), "REFUND.ABNORMAL"));
    assertEquals("ABNORMAL", channelState());
    assertEquals("FAILED", taskState());
    db.update("UPDATE channel_refund SET state='PROCESSING' WHERE request_id=9001");
    deliver(fixture.notice(notification("CLOSED"), "REFUND.CLOSED"));
    assertEquals("CLOSED", channelState());
    assertEquals("FAILED", taskState());
    assertEquals(2, db.count("SELECT COUNT(*) FROM payment_callback"));
  }

  @Test
  void aLateAbnormalNotificationCannotBlockAnAlreadyConfirmedSuccessfulRefund() {
    deliver(fixture.notice(notification("SUCCESS"), "REFUND.SUCCESS"));
    deliver(fixture.notice(notification("ABNORMAL"), "REFUND.ABNORMAL"));
    assertEquals("SUCCESS", channelState());
    assertEquals("REQUESTED", taskState());
  }

  @Test
  void rejectsWrongProviderAndMismatchingAmountsWithoutPersistingAnAcknowledgement() {
    var wrong = notification("SUCCESS");
    wrong.put("sp_mchid", "9999999999");
    assertEquals(
        "REFUND_MISMATCH",
        assertThrows(
                BusinessException.class, () -> deliver(fixture.notice(wrong, "REFUND.SUCCESS")))
            .code());
    var amount = notification("SUCCESS");
    amount.put(
        "amount", Map.of("total", 1000, "refund", 501, "payer_total", 1000, "payer_refund", 501));
    assertEquals(
        "REFUND_MISMATCH",
        assertThrows(
                BusinessException.class, () -> deliver(fixture.notice(amount, "REFUND.SUCCESS")))
            .code());
    assertEquals("PROCESSING", channelState());
    assertEquals(0, db.count("SELECT COUNT(*) FROM payment_callback"));
  }

  private static Map<String, Object> notification(String state) {
    var data = new LinkedHashMap<String, Object>();
    data.put("sp_mchid", "1900000100");
    data.put("sub_mchid", "1900000109");
    data.put("transaction_id", "WXORDER1");
    data.put("out_trade_no", "ORDER1");
    data.put("refund_id", "WXREFUND1");
    data.put("out_refund_no", "REFUND1");
    data.put("refund_status", state);
    if (state.equals("SUCCESS")) data.put("success_time", OffsetDateTime.now().toString());
    data.put("user_received_account", "支付用户零钱");
    data.put(
        "amount", Map.of("total", 1000, "refund", 500, "payer_total", 1000, "payer_refund", 500));
    return data;
  }

  private void deliver(PaymentIntegrationTest.Fixture.Notice notice) {
    refunds.notification(
        PaymentIntegrationTest.Fixture.PUBLIC_ID,
        notice.timestamp(),
        notice.nonce(),
        notice.signature(),
        notice.body());
  }

  private String channelState() {
    return db.one("SELECT state FROM channel_refund WHERE request_id=9001").get("state").toString();
  }

  private String taskState() {
    return db.one("SELECT state FROM refund_request WHERE id=9001").get("state").toString();
  }
}
