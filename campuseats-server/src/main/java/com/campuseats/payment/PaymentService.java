package com.campuseats.payment;

import static com.campuseats.common.BusinessException.require;
import static com.campuseats.payment.WechatPayChannel.*;

import com.campuseats.common.BusinessException;
import com.campuseats.common.Hashes;
import com.campuseats.infra.*;
import com.campuseats.platform.*;
import com.campuseats.security.*;
import com.wechat.pay.java.core.exception.ServiceException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
  @io.swagger.v3.oas.annotations.media.Schema(name = "PaymentIntent")
  public record View(
      String id,
      long checkoutId,
      String channel,
      String state,
      LocalDateTime expiresAt,
      Map<String, Object> action,
      String lastError,
      boolean closeRequested) {}

  private final Db db;
  private final Json json;
  private final Clock clock;
  private final WechatPaySettings settings;
  private final WechatPayChannel channel;
  private final CheckoutService checkouts;
  private final UserIdentityService identities;
  private final TransactionTemplate tx;

  public PaymentService(
      Db db,
      Json json,
      Clock clock,
      WechatPaySettings settings,
      WechatPayChannel channel,
      CheckoutService checkouts,
      UserIdentityService identities,
      PlatformTransactionManager manager) {
    this.db = db;
    this.json = json;
    this.clock = clock;
    this.settings = settings;
    this.channel = channel;
    this.checkouts = checkouts;
    this.identities = identities;
    tx = new TransactionTemplate(manager);
  }

  public Map<String, Object> capabilities() {
    return Map.of(
        "provider",
        settings.enabled ? "WECHAT" : "MOCK",
        "channels",
        settings.enabled ? List.of("MINI_PROGRAM", "JSAPI", "H5", "NATIVE") : List.of("MOCK"));
  }

  public View create(long checkoutId, String key, String scene, long version, String ip) {
    long user = Actor.user().id();
    require(
        key != null && key.matches("[A-Za-z0-9_-]{1,64}"), 400, "IDEMPOTENCY_KEY", "支付请求编号格式不正确");
    require(settings.enabled, 503, "PAYMENT_UNCONFIGURED", "真实支付尚未接入");
    require(
        Set.of("MINI_PROGRAM", "JSAPI", "H5", "NATIVE").contains(scene),
        400,
        "PAYMENT_CHANNEL",
        "支付场景不正确");
    var saved =
        tx.execute(
            s -> {
              var checkout =
                  db.one(
                      "SELECT * FROM checkout WHERE id=? AND user_id=? FOR UPDATE",
                      checkoutId,
                      user);
              require(checkout != null, 404, "NOT_FOUND", "结算单不存在");
              var old =
                  db.one(
                      "SELECT * FROM payment_intent WHERE user_id=? AND request_key=?", user, key);
              if (old != null) {
                require(
                    Db.id(old, "checkout_id") == checkoutId && old.get("channel").equals(scene),
                    409,
                    "PAYMENT_CONFLICT",
                    "支付编号不能更换内容");
                return old;
              }
              String openid =
                  Db.id(checkout, "total_cents") > 0
                          && Set.of("MINI_PROGRAM", "JSAPI").contains(scene)
                      ? identities.openid(user, settings.appId(scene))
                      : null;
              require(
                  checkout.get("state").equals("PENDING") && Db.id(checkout, "version") == version,
                  409,
                  "CHECKOUT_STATE",
                  "结算单状态或版本已变化");
              require(
                  now().isBefore(Db.time(checkout.get("expires_at"))),
                  409,
                  "CHECKOUT_EXPIRED",
                  "结算单已过期");
              require(
                  checkout.get("payment_provider").equals("WECHAT"),
                  409,
                  "PAYMENT_PROVIDER",
                  "历史模拟结算单不能用于真实支付");
              require(
                  db.count("SELECT COUNT(*) FROM payment_intent WHERE checkout_id=?", checkoutId)
                      == 0,
                  409,
                  "PAYMENT_EXISTS",
                  "结算单已有支付请求，请查询原支付状态");
              String id = UUID.randomUUID().toString().replace("-", "");
              db.update(
                  "INSERT INTO"
                      + " payment_intent(id,checkout_id,user_id,request_key,channel,state,app_id,total_cents,expires_at,created_at,updated_at,next_check_at,payer_openid,client_ip,notify_base)"
                      + " VALUES(?,?,?,?,?,'NEW',?,?,?,?,?,?,?,?,?)",
                  id,
                  checkoutId,
                  user,
                  key,
                  scene,
                  settings.appId(scene),
                  checkout.get("total_cents"),
                  checkout.get("expires_at"),
                  now(),
                  now(),
                  now(),
                  openid,
                  ip,
                  settings.notifyBase);
              for (var order :
                  db.rows("SELECT * FROM orders WHERE checkout_id=? ORDER BY id", checkoutId)) {
                var merchant =
                    db.one(
                        "SELECT * FROM merchant_payment_config WHERE store_id=? AND enabled=true"
                            + " FOR SHARE",
                        order.get("store_id"));
                require(merchant != null, 409, "MERCHANT_PAYMENT_UNAVAILABLE", "店铺尚未启用真实收款");
                db.update(
                    "INSERT INTO"
                        + " payment_channel_order(intent_id,order_id,store_id,out_trade_no,sub_mchid,amount_cents)"
                        + " VALUES(?,?,?,?,?,?)",
                    id,
                    order.get("id"),
                    order.get("store_id"),
                    "CE" + order.get("id") + "_" + id.substring(0, 8),
                    merchant.get("sub_mchid"),
                    ((java.math.BigDecimal) order.get("amount"))
                        .movePointRight(2)
                        .longValueExact());
              }
              var intent = read(id);
              if (Db.id(intent, "total_cents") == 0) {
                db.update("UPDATE payment_intent SET state='SUCCEEDED' WHERE id=?", id);
                checkouts.completeChannelPayment(checkoutId, references(id));
              }
              return read(id);
            });
    if (saved.get("state").equals("NEW")) drive(saved.get("id").toString());
    return view(read(saved.get("id").toString()));
  }

  public View status(long checkoutId) {
    var row =
        db.one("SELECT id FROM checkout WHERE id=? AND user_id=?", checkoutId, Actor.user().id());
    require(row != null, 404, "NOT_FOUND", "结算单不存在");
    var intent = db.one("SELECT * FROM payment_intent WHERE checkout_id=?", checkoutId);
    require(intent != null, 404, "PAYMENT_NOT_FOUND", "尚未发起支付");
    return view(intent);
  }

  public Map<String, Object> cancel(long id, long version) {
    checkouts.cancel(id, version);
    var intent = db.one("SELECT * FROM payment_intent WHERE checkout_id=?", id);
    if (intent != null && Db.bool(intent, "close_requested")) drive(intent.get("id").toString());
    return checkouts.detail(id);
  }

  public void drive(String id) {
    String lease = UUID.randomUUID().toString();
    int claimed =
        db.update(
            "UPDATE payment_intent SET lease_token=?,lease_until=? WHERE id=? AND state NOT IN"
                + " ('SUCCEEDED','CLOSED','FAILED') AND (lease_until IS NULL OR lease_until<?)",
            lease,
            now().plusSeconds(30),
            id,
            now());
    if (claimed == 0) return;
    var intent = read(id);
    try {
      if (intent.get("state").equals("NEW")
          && !Db.bool(intent, "close_requested")
          && now().isBefore(Db.time(intent.get("expires_at")))) {
        db.update(
            "UPDATE payment_intent SET submitted=true,state='UNKNOWN' WHERE id=? AND lease_token=?",
            id,
            lease);
        var action = channel.prepay(intent.get("channel").toString(), payload(intent));
        db.update(
            "UPDATE payment_intent SET"
                + " action_json=?,state='PENDING',attempts=0,last_error=NULL,next_check_at=?,updated_at=?"
                + " WHERE id=? AND lease_token=? AND state NOT IN ('SUCCEEDED','CLOSED')",
            json.write(action),
            now().plusSeconds(15),
            now(),
            id,
            lease);
      } else {
        boolean closing =
            Db.bool(intent, "close_requested")
                || !now().isBefore(Db.time(intent.get("expires_at")));
        if (!Db.bool(intent, "submitted") && closing) {
          finishClosed(intent, lease);
          return;
        }
        Map<String, Object> result;
        try {
          result = query(intent);
        } catch (ServiceException ex) {
          if (!Set.of("ORDER_NOT_EXIST", "ORDERNOTEXIST").contains(ex.getErrorCode())) throw ex;
          if (!closing) {
            var action = channel.prepay(intent.get("channel").toString(), payload(intent));
            db.update(
                "UPDATE payment_intent SET"
                    + " action_json=?,state='PENDING',attempts=0,last_error=NULL,next_check_at=?"
                    + " WHERE id=? AND lease_token=? AND state NOT IN ('SUCCEEDED','CLOSED')",
                json.write(action),
                now().plusSeconds(15),
                id,
                lease);
            return;
          }
          // A previous request may have timed out after acceptance. Missing query results alone
          // never release resources.
          close(intent);
          finishClosed(intent, lease);
          return;
        }
        String state = applyResult(intent, result);
        if (state.equals("CLOSED")) {
          finishClosed(intent, lease);
          return;
        }
        if (!state.equals("SUCCEEDED") && closing) {
          db.update(
              "UPDATE payment_intent SET state='CLOSING',close_requested=true WHERE id=? AND"
                  + " lease_token=? AND state<>'SUCCEEDED'",
              id,
              lease);
          close(intent);
          finishClosed(intent, lease);
        } else if (state.equals("PENDING")) {
          String restoredAction =
              intent.get("action_json") == null
                  ? json.write(channel.prepay(intent.get("channel").toString(), payload(intent)))
                  : null;
          db.update(
              "UPDATE payment_intent SET"
                  + " state='PENDING',action_json=COALESCE(?,action_json),next_check_at=?,attempts=0,last_error=NULL"
                  + " WHERE id=? AND lease_token=? AND state NOT IN ('SUCCEEDED','CLOSED')",
              restoredAction,
              now().plusSeconds(15),
              id,
              lease);
        } else
          db.update(
              "UPDATE payment_intent SET next_check_at=?,attempts=0,last_error=NULL WHERE id=? AND"
                  + " lease_token=?",
              now().plusSeconds(15),
              id,
              lease);
      }
    } catch (Exception ex) {
      String error =
          ex instanceof BusinessException b
              ? b.code()
              : ex instanceof ServiceException e ? e.getErrorCode() : "CHANNEL_UNKNOWN";
      org.slf4j.LoggerFactory.getLogger(PaymentService.class)
          .warn(
              "Channel attempt unresolved; intentId={} type={} code={}",
              id.replaceAll("[\\r\\n]", "_"),
              ex.getClass().getSimpleName(),
              String.valueOf(error).replaceAll("[\\r\\n]", "_"));
      db.update(
          "UPDATE payment_intent SET state=CASE WHEN attempts>=4 THEN 'REVIEW' ELSE 'UNKNOWN'"
              + " END,attempts=attempts+1,last_error=?,next_check_at=?,updated_at=? WHERE id=? AND"
              + " lease_token=? AND state NOT IN ('SUCCEEDED','CLOSED')",
          error,
          now().plusSeconds(30),
          now(),
          id,
          lease);
    } finally {
      db.update(
          "UPDATE payment_intent SET lease_token=NULL,lease_until=NULL WHERE id=? AND"
              + " lease_token=?",
          id,
          lease);
    }
  }

  private Map<String, Object> query(Map<String, Object> i) {
    return channel.request(
        "GET",
        "/v3/combine-transactions/out-trade-no/"
            + i.get("id")
            + "?combine_mchid="
            + settings.merchantId,
        Map.of());
  }

  private void close(Map<String, Object> i) {
    var orders =
        parts(i.get("id").toString()).stream()
            .filter(p -> Db.id(p, "amount_cents") > 0)
            .map(
                p ->
                    Map.of(
                        "mchid",
                        settings.merchantId,
                        "sub_mchid",
                        p.get("sub_mchid"),
                        "out_trade_no",
                        p.get("out_trade_no")))
            .toList();
    channel.request(
        "POST",
        "/v3/combine-transactions/out-trade-no/" + i.get("id") + "/close",
        Map.of("combine_appid", i.get("app_id"), "sub_orders", orders));
  }

  private Map<String, Object> payload(Map<String, Object> i) {
    var orders =
        parts(i.get("id").toString()).stream()
            .filter(p -> Db.id(p, "amount_cents") > 0)
            .map(
                p ->
                    Map.of(
                        "mchid",
                        settings.merchantId,
                        "sub_mchid",
                        p.get("sub_mchid"),
                        "out_trade_no",
                        p.get("out_trade_no"),
                        "attach",
                        "CampusEats",
                        "description",
                        "校园点餐",
                        "amount",
                        Map.of("total_amount", p.get("amount_cents"), "currency", "CNY")))
            .toList();
    var result = new LinkedHashMap<String, Object>();
    result.put("combine_appid", i.get("app_id"));
    result.put("combine_mchid", settings.merchantId);
    result.put("combine_out_trade_no", i.get("id"));
    result.put("sub_orders", orders);
    result.put(
        "time_expire", Db.time(i.get("expires_at")).atOffset(ZoneOffset.ofHours(8)).toString());
    result.put("notify_url", i.get("notify_base") + "/api/v1/payments/wechat/notify");
    var scene = new LinkedHashMap<String, Object>();
    scene.put("payer_client_ip", i.get("client_ip"));
    if (i.get("channel").equals("H5"))
      scene.put(
          "h5_info",
          Map.of("type", "Wap", "app_name", "CampusEats", "app_url", i.get("notify_base")));
    result.put("scene_info", scene);
    if (i.get("payer_openid") != null)
      result.put("combine_payer_info", Map.of("openid", i.get("payer_openid")));
    return result;
  }

  @SuppressWarnings("unchecked")
  String applyResult(Map<String, Object> intent, Map<String, Object> data) {
    require(
        text(data, "combine_mchid").equals(settings.merchantId)
            && text(data, "combine_appid").equals(intent.get("app_id"))
            && text(data, "combine_out_trade_no").equals(intent.get("id")),
        409,
        "PAYMENT_MISMATCH",
        "支付主体或单号不匹配");
    require(data.get("sub_orders") instanceof List, 502, "CHANNEL_RESPONSE", "渠道缺少子单");
    var expected =
        parts(intent.get("id").toString()).stream()
            .filter(p -> Db.id(p, "amount_cents") > 0)
            .toList();
    var actual = (List<Map<String, Object>>) data.get("sub_orders");
    require(expected.size() == actual.size(), 409, "PAYMENT_MISMATCH", "支付子单数量不匹配");
    var states = new HashSet<String>();
    var refs = new LinkedHashMap<Long, String>();
    var seen = new HashSet<String>();
    for (var p : actual) {
      String trade = text(p, "out_trade_no");
      require(seen.add(trade), 409, "PAYMENT_MISMATCH", "渠道重复子单");
      var own =
          expected.stream()
              .filter(e -> e.get("out_trade_no").equals(trade))
              .findFirst()
              .orElseThrow(() -> new BusinessException(409, "PAYMENT_MISMATCH", "未知支付子单"));
      var amount = object(p, "amount");
      require(
          text(p, "mchid").equals(settings.merchantId)
              && text(p, "sub_mchid").equals(own.get("sub_mchid"))
              && amount(amount, "total_amount") == Db.id(own, "amount_cents")
              && text(amount, "currency").equals("CNY"),
          409,
          "PAYMENT_MISMATCH",
          "子单主体或金额不匹配");
      String state = text(p, "trade_state");
      require(
          Set.of("SUCCESS", "CLOSED", "NOTPAY", "USERPAYING").contains(state),
          502,
          "PAYMENT_CHANNEL_STATE",
          "渠道交易状态需要核对：" + state);
      states.add(state);
      if (state.equals("SUCCESS")) refs.put(Db.id(own, "order_id"), text(p, "transaction_id"));
    }
    if (states.equals(Set.of("SUCCESS"))) {
      tx.executeWithoutResult(
          s -> {
            db.one("SELECT id FROM checkout WHERE id=? FOR UPDATE", intent.get("checkout_id"));
            var current =
                db.one("SELECT * FROM payment_intent WHERE id=? FOR UPDATE", intent.get("id"));
            require(
                !current.get("state").equals("CLOSED"),
                409,
                "PAYMENT_CONFLICT",
                "已关闭支付出现成功结果，需要人工核对");
            for (var p : parts(intent.get("id").toString())) {
              String ref =
                  Db.id(p, "amount_cents") == 0
                      ? "ZERO-" + p.get("order_id")
                      : refs.get(Db.id(p, "order_id"));
              require(
                  p.get("transaction_id") == null || p.get("transaction_id").equals(ref),
                  409,
                  "PAYMENT_CONFLICT",
                  "渠道交易编号发生变化");
              LocalDateTime occurred =
                  Db.id(p, "amount_cents") == 0
                      ? now()
                      : OffsetDateTime.parse(
                              text(
                                  actualFor(data, p.get("out_trade_no").toString()),
                                  "success_time"))
                          .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                          .toLocalDateTime();
              db.update(
                  "UPDATE payment_channel_order SET transaction_id=?,success_time=? WHERE"
                      + " intent_id=? AND order_id=?",
                  ref,
                  occurred,
                  intent.get("id"),
                  p.get("order_id"));
            }
            checkouts.completeChannelPayment(
                Db.id(intent, "checkout_id"), references(intent.get("id").toString()));
            db.update(
                "UPDATE payment_intent SET"
                    + " state='SUCCEEDED',action_json=NULL,last_error=NULL,updated_at=? WHERE id=?",
                now(),
                intent.get("id"));
          });
      return "SUCCEEDED";
    }
    require(!states.contains("SUCCESS"), 409, "PAYMENT_MISMATCH", "合单返回部分支付成功，需人工核对");
    if (states.equals(Set.of("CLOSED"))) return "CLOSED";
    return "PENDING";
  }

  private void finishClosed(Map<String, Object> intent, String lease) {
    tx.executeWithoutResult(
        s -> {
          var checkout =
              db.one("SELECT * FROM checkout WHERE id=? FOR UPDATE", intent.get("checkout_id"));
          var own = db.one("SELECT * FROM payment_intent WHERE id=? FOR UPDATE", intent.get("id"));
          if (own.get("state").equals("SUCCEEDED")
              || !Objects.equals(own.get("lease_token"), lease)) return;
          require(!checkout.get("state").equals("PAID"), 409, "PAYMENT_CONFLICT", "已付款结算单不能关闭");
          db.update(
              "UPDATE payment_intent SET"
                  + " state='CLOSED',action_json=NULL,last_error=NULL,updated_at=? WHERE id=?",
              now(),
              intent.get("id"));
          if (checkout.get("state").equals("PENDING")) checkouts.cancelLocked(checkout);
        });
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> actualFor(Map<String, Object> data, String trade) {
    return ((List<Map<String, Object>>) data.get("sub_orders"))
        .stream().filter(p -> p.get("out_trade_no").equals(trade)).findFirst().orElseThrow();
  }

  public void notification(
      String serial, String timestamp, String nonce, String signature, String body) {
    var data = channel.notification(serial, timestamp, nonce, signature, body);
    String id = text(data, "combine_out_trade_no");
    var intent = read(id);
    require(intent != null, 404, "PAYMENT_NOT_FOUND", "支付请求不存在");
    tx.executeWithoutResult(
        s -> {
          String event = text(json.map(body), "id"), hash = Hashes.digest("SHA-256", body);
          var old = db.one("SELECT * FROM payment_callback WHERE event_id=?", event);
          require(
              old == null || old.get("body_sha256").equals(hash),
              409,
              "CALLBACK_CONFLICT",
              "回调编号内容冲突");
          require(
              applyResult(intent, data).equals("SUCCEEDED"), 400, "CALLBACK_STATE", "支付通知不是成功状态");
          db.update(
              "INSERT INTO payment_callback(event_id,kind,body_sha256,created_at)"
                  + " VALUES(?,'PAYMENT',?,?) ON DUPLICATE KEY UPDATE event_id=event_id",
              event,
              hash,
              now());
        });
  }

  public List<Map<String, Object>> exceptions() {
    require(Actor.employee().role().equals("PLATFORM_ADMIN"), 403, "FORBIDDEN", "需要平台权限");
    return db.rows(
        "SELECT id,checkout_id,state,last_error,attempts,close_requested,updated_at FROM"
            + " payment_intent WHERE state IN ('UNKNOWN','REVIEW','CLOSING') ORDER BY updated_at"
            + " LIMIT 200");
  }

  public void retry(String id) {
    require(Actor.employee().role().equals("PLATFORM_ADMIN"), 403, "FORBIDDEN", "需要平台权限");
    require(
        db.update(
                "UPDATE payment_intent SET state='UNKNOWN',attempts=0,next_check_at=? WHERE id=?"
                    + " AND state='REVIEW'",
                now(),
                id)
            == 1,
        409,
        "PAYMENT_STATE",
        "仅人工核对状态可以重查");
    drive(id);
  }

  public void poll(java.util.function.BooleanSupplier budget) {
    for (var i :
        db.rows(
            "SELECT id FROM payment_intent WHERE state IN ('NEW','PENDING','UNKNOWN','CLOSING') AND"
                + " next_check_at<=? ORDER BY next_check_at LIMIT 20",
            now())) {
      if (!budget.getAsBoolean()) break;
      drive(i.get("id").toString());
    }
  }

  private Map<String, Object> read(String id) {
    return db.one("SELECT * FROM payment_intent WHERE id=?", id);
  }

  private List<Map<String, Object>> parts(String id) {
    return db.rows("SELECT * FROM payment_channel_order WHERE intent_id=? ORDER BY order_id", id);
  }

  private Map<Long, String> references(String id) {
    var refs = new LinkedHashMap<Long, String>();
    for (var p : parts(id))
      refs.put(
          Db.id(p, "order_id"),
          p.get("transaction_id") == null
              ? "ZERO-" + p.get("order_id")
              : p.get("transaction_id").toString());
    return refs;
  }

  private View view(Map<String, Object> i) {
    return new View(
        i.get("id").toString(),
        Db.id(i, "checkout_id"),
        i.get("channel").toString(),
        i.get("state").toString(),
        Db.time(i.get("expires_at")),
        i.get("action_json") == null
                || !i.get("state").equals("PENDING")
                || Db.bool(i, "close_requested")
            ? Map.of()
            : json.map(i.get("action_json").toString()),
        (String) i.get("last_error"),
        Db.bool(i, "close_requested"));
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
