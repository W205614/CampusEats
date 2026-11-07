package com.campuseats.payment;

import static com.campuseats.common.BusinessException.require;
import static com.campuseats.payment.WechatPayChannel.*;

import com.campuseats.common.Hashes;
import com.campuseats.infra.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ChannelRefunds {
  private final Db db;
  private final Json json;
  private final Clock clock;
  private final WechatPayChannel channel;
  private final WechatPaySettings settings;
  private final TransactionTemplate tx;

  public ChannelRefunds(
      Db db,
      Json json,
      Clock clock,
      WechatPayChannel channel,
      WechatPaySettings settings,
      PlatformTransactionManager manager) {
    this.db = db;
    this.json = json;
    this.clock = clock;
    this.channel = channel;
    this.settings = settings;
    tx = new TransactionTemplate(manager);
  }

  public boolean real(long orderId) {
    return db.count(
            "SELECT COUNT(*) FROM orders o JOIN checkout c ON c.id=o.checkout_id WHERE o.id=? AND"
                + " c.payment_provider='WECHAT'",
            orderId)
        == 1;
  }

  public boolean complete(Map<String, Object> task) {
    if (Db.id(task, "amount_cents") == 0) return true;
    long id = Db.id(task, "id");
    var saved =
        tx.execute(
            s -> {
              var existing =
                  db.one("SELECT * FROM channel_refund WHERE request_id=? FOR UPDATE", id);
              if (existing != null) return existing;
              var part =
                  db.one(
                      "SELECT p.*,i.notify_base FROM payment_channel_order p JOIN payment_intent i"
                          + " ON i.id=p.intent_id WHERE p.order_id=? AND i.state='SUCCEEDED'",
                      task.get("order_id"));
              require(part != null, 409, "PAYMENT_RECEIPT_MISSING", "原始支付凭据不存在");
              db.update(
                  "INSERT INTO"
                      + " channel_refund(request_id,intent_id,sub_mchid,out_trade_no,out_refund_no,amount_cents,total_cents,state,updated_at,notify_base)"
                      + " VALUES(?,?,?,?,?,?,?,'NEW',?,?)",
                  id,
                  part.get("intent_id"),
                  part.get("sub_mchid"),
                  part.get("out_trade_no"),
                  "CER" + id,
                  task.get("amount_cents"),
                  part.get("amount_cents"),
                  now(),
                  part.get("notify_base"));
              return db.one("SELECT * FROM channel_refund WHERE request_id=?", id);
            });
    if (saved.get("state").equals("SUCCESS") && saved.get("accepted_at") != null) return true;
    require(
        !Set.of("CLOSED", "ABNORMAL").contains(saved.get("state")),
        409,
        "REFUND_CHANNEL_FAILED",
        "渠道退款异常，请人工处理");
    Map<String, Object> result;
    if (Db.bool(saved, "submitted")) {
      try {
        result =
            channel.request(
                "GET",
                "/v3/refund/domestic/refunds/"
                    + saved.get("out_refund_no")
                    + "?sub_mchid="
                    + saved.get("sub_mchid"),
                Map.of());
      } catch (com.wechat.pay.java.core.exception.ServiceException ex) {
        if (!ex.getErrorCode().equals("RESOURCE_NOT_EXISTS")) throw ex;
        result = submit(saved);
      }
    } else {
      db.update(
          "UPDATE channel_refund SET submitted=true,state='UNKNOWN',updated_at=? WHERE"
              + " request_id=?",
          now(),
          id);
      result = submit(saved);
    }
    apply(saved, result, false);
    String state =
        db.one("SELECT state FROM channel_refund WHERE request_id=?", id).get("state").toString();
    require(
        !Set.of("CLOSED", "ABNORMAL").contains(state),
        409,
        "REFUND_CHANNEL_FAILED",
        "渠道退款异常，请人工处理");
    return state.equals("SUCCESS");
  }

  private Map<String, Object> submit(Map<String, Object> saved) {
    return channel.request(
        "POST",
        "/v3/refund/domestic/refunds",
        Map.of(
            "sub_mchid",
            saved.get("sub_mchid"),
            "out_trade_no",
            saved.get("out_trade_no"),
            "out_refund_no",
            saved.get("out_refund_no"),
            "notify_url",
            saved.get("notify_base") + "/api/v1/payments/wechat/refund-notify",
            "amount",
            Map.of(
                "refund",
                saved.get("amount_cents"),
                "total",
                saved.get("total_cents"),
                "currency",
                "CNY")));
  }

  private boolean apply(Map<String, Object> own, Map<String, Object> result, boolean callback) {
    var amount = object(result, "amount");
    require(
        text(result, "out_refund_no").equals(own.get("out_refund_no"))
            && text(result, "out_trade_no").equals(own.get("out_trade_no"))
            && text(result, "sub_mchid").equals(own.get("sub_mchid"))
            && amount(amount, "refund") == Db.id(own, "amount_cents")
            && amount(amount, "total") == Db.id(own, "total_cents"),
        409,
        "REFUND_MISMATCH",
        "退款主体或金额不匹配");
    if (callback)
      require(
          text(result, "sp_mchid").equals(settings.merchantId), 409, "REFUND_MISMATCH", "退款服务商不匹配");
    else require(text(amount, "currency").equals("CNY"), 409, "REFUND_MISMATCH", "退款币种不匹配");
    String state = text(result, callback ? "refund_status" : "status");
    require(
        Set.of("SUCCESS", "PROCESSING", "CLOSED", "ABNORMAL").contains(state),
        502,
        "REFUND_STATE",
        "未知渠道退款状态");
    LocalDateTime succeeded =
        state.equals("SUCCESS")
            ? OffsetDateTime.parse(text(result, "success_time"))
                .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                .toLocalDateTime()
            : null;
    String provider = text(result, "refund_id");
    LocalDateTime accepted =
        callback
            ? null
            : OffsetDateTime.parse(text(result, "create_time"))
                .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                .toLocalDateTime();
    tx.executeWithoutResult(
        s -> {
          var current =
              db.one(
                  "SELECT * FROM channel_refund WHERE request_id=? FOR UPDATE",
                  own.get("request_id"));
          require(
              current.get("provider_id") == null || current.get("provider_id").equals(provider),
              409,
              "REFUND_MISMATCH",
              "退款渠道编号变化");
          if (accepted != null)
            db.update(
                "UPDATE channel_refund SET accepted_at=COALESCE(accepted_at,?) WHERE request_id=?",
                accepted,
                own.get("request_id"));
          if (current.get("state").equals("SUCCESS")) return;
          db.update(
              "UPDATE channel_refund SET state=?,provider_id=?,succeeded_at=?,updated_at=? WHERE"
                  + " request_id=?",
              state,
              provider,
              succeeded,
              now(),
              own.get("request_id"));
        });
    return state.equals("SUCCESS");
  }

  public void notification(
      String serial, String timestamp, String nonce, String signature, String body) {
    var data = channel.notification(serial, timestamp, nonce, signature, body);
    var own =
        db.one("SELECT * FROM channel_refund WHERE out_refund_no=?", text(data, "out_refund_no"));
    require(own != null, 404, "NOT_FOUND", "退款请求不存在");
    tx.executeWithoutResult(
        s -> {
          String event = text(json.map(body), "id"), hash = Hashes.digest("SHA-256", body);
          var old = db.one("SELECT body_sha256 FROM payment_callback WHERE event_id=?", event);
          require(
              old == null || old.get("body_sha256").equals(hash),
              409,
              "CALLBACK_CONFLICT",
              "回调编号内容冲突");
          apply(own, data, true);
          db.update(
              "INSERT INTO payment_callback(event_id,kind,body_sha256,created_at)"
                  + " VALUES(?,'REFUND',?,?) ON DUPLICATE KEY UPDATE event_id=event_id",
              event,
              hash,
              now());
          boolean failed =
              Set.of("CLOSED", "ABNORMAL")
                  .contains(
                      db.one(
                              "SELECT state FROM channel_refund WHERE request_id=?",
                              own.get("request_id"))
                          .get("state"));
          db.update(
              "UPDATE refund_request SET"
                  + " state=?,last_error=?,next_attempt_at=?,attempts=0,lease_token=NULL,lease_until=NULL"
                  + " WHERE id=? AND state<>'SUCCEEDED'",
              failed ? "FAILED" : "REQUESTED",
              failed ? "REFUND_CHANNEL_FAILED" : null,
              now(),
              own.get("request_id"));
        });
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
