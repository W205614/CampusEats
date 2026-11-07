package com.campuseats.integration;

import static com.campuseats.common.BusinessException.require;

import com.campuseats.common.BusinessException;
import com.campuseats.infra.*;
import com.campuseats.security.Actor;
import com.campuseats.security.UserIdentityService;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable work is created with business events; only the worker calls external networks. */
@Service
public class ExternalNotifications {
  static final Set<String> EVENTS =
      Set.of(
          "ORDER_PAID",
          "ORDER_READY",
          "ORDER_PICKUP",
          "ORDER_COMPLETE",
          "ORDER_COMPLETED",
          "ORDER_CANCELLED",
          "ORDER_EXCEPTION_CANCELLED",
          "ORDER_REFUNDED");
  private final Db db;
  private final Clock clock;
  private final Environment environment;
  private final TencentSmsGateway sms;
  private final WechatSubscriptionGateway wechat;
  private final UserIdentityService identities;
  private final Json json;
  private final TransactionTemplate tx;
  private final RuntimeOperations operations;

  public record Template(String event, String templateId) {}

  public record SubscriptionTemplates(boolean enabled, List<Template> templates) {}

  public ExternalNotifications(
      Db db,
      Clock clock,
      Environment environment,
      TencentSmsGateway sms,
      WechatSubscriptionGateway wechat,
      UserIdentityService identities,
      Json json,
      PlatformTransactionManager manager,
      RuntimeOperations operations) {
    this.db = db;
    this.clock = clock;
    this.environment = environment;
    this.sms = sms;
    this.wechat = wechat;
    this.identities = identities;
    this.json = json;
    this.tx = new TransactionTemplate(manager);
    this.operations = operations;
  }

  public void enqueue(long event, long order, String type, String summary) {
    if (!enabled() || !EVENTS.contains(type)) return;
    var o = db.one("SELECT user_id FROM orders WHERE id=?", order);
    for (String channel : List.of("SMS", "WECHAT")) {
      String template = template(channel, type);
      if (template.isBlank()) continue;
      String fields =
          environment.getProperty(
              "campus.notifications." + channel.toLowerCase(Locale.ROOT) + ".fields." + type, "{}");
      db.update(
          "INSERT IGNORE INTO"
              + " notification_delivery(event_id,user_id,order_id,channel,template_id,event_type,field_mapping,summary,next_attempt_at,created_at,updated_at)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
          event,
          o.get("user_id"),
          order,
          channel,
          template,
          type,
          fields,
          summary,
          now(),
          now(),
          now());
    }
  }

  public SubscriptionTemplates templates() {
    return new SubscriptionTemplates(
        enabled() && wechat.configured(),
        EVENTS.stream()
            .sorted()
            .filter(e -> !template("WECHAT", e).isBlank())
            .map(e -> new Template(e, template("WECHAT", e)))
            .toList());
  }

  public void authorize(String template, boolean accepted) {
    Actor actor = Actor.user();
    long user = actor.id();
    require(
        templates().templates().stream().anyMatch(t -> t.templateId().equals(template)),
        400,
        "INVALID_TEMPLATE",
        "订阅模板不存在");
    tx.executeWithoutResult(
        status -> {
          identities.requireCurrentUser(actor);
          if (accepted) {
            identities.openid(user, wechat.appId());
            db.update(
                "INSERT INTO"
                    + " notification_subscription(user_id,app_id,template_id,credits,updated_at)"
                    + " VALUES(?,?,?,1,?) ON DUPLICATE KEY UPDATE"
                    + " credits=LEAST(credits+1,100),updated_at=VALUES(updated_at)",
                user,
                wechat.appId(),
                template,
                now());
          } else
            db.update(
                "UPDATE notification_subscription SET credits=0,updated_at=? WHERE user_id=? AND"
                    + " app_id=? AND template_id=?",
                now(),
                user,
                wechat.appId(),
                template);
        });
  }

  public List<Map<String, Object>> deliveries(
      Long before, Integer size, boolean platform, String state) {
    int limit = size == null ? 50 : size;
    require(limit >= 1 && limit <= 200, 400, "INVALID_LIMIT", "每页数量须为1至200");
    long user = platform ? 0 : Actor.user().id();
    if (platform)
      require(Actor.employee().role().equals("PLATFORM_ADMIN"), 403, "FORBIDDEN", "需要平台管理员权限");
    require(
        state == null
            || state.isBlank()
            || Set.of(
                    "REQUESTED",
                    "PROCESSING",
                    "ACCEPTED",
                    "SUCCEEDED",
                    "FAILED",
                    "UNKNOWN",
                    "SKIPPED")
                .contains(state),
        400,
        "INVALID_STATE",
        "通知状态不正确");
    var args = new ArrayList<Object>();
    args.add(before == null ? Long.MAX_VALUE : before);
    if (!platform) args.add(user);
    if (state != null && !state.isBlank()) args.add(state);
    args.add(limit);
    return db.rows(
        "SELECT id,event_id,order_id,channel,state,attempts,last_error,created_at,updated_at FROM"
            + " notification_delivery WHERE id<?"
            + (platform ? "" : " AND user_id=?")
            + (state == null || state.isBlank() ? "" : " AND state=?")
            + " ORDER BY id DESC LIMIT ?",
        args.toArray());
  }

  @Scheduled(fixedDelay = 2000)
  public void work() {
    if (!enabled() || !environment.getProperty("campus.tasks-enabled", Boolean.class, true)) return;
    operations.run(
        "externalNotifications",
        round -> {
          for (int i = 0; i < 10 && round.withinBudget(); i++) if (!deliver()) break;
        });
  }

  public boolean deliver() {
    // A dead sender may have reached the provider; its abandoned lease is never automatically
    // resent.
    db.update(
        "UPDATE notification_delivery SET"
            + " state='UNKNOWN',lease_token=NULL,lease_until=NULL,last_error='SENDER_INTERRUPTED',updated_at=?"
            + " WHERE state='PROCESSING' AND lease_until<?",
        now(),
        now());
    var task =
        tx.execute(
            status -> {
              var row =
                  db.one(
                      "SELECT * FROM notification_delivery WHERE state='REQUESTED' AND"
                          + " next_attempt_at<=? ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",
                      now());
              if (row == null) return null;
              String token = UUID.randomUUID().toString();
              db.update(
                  "UPDATE notification_delivery SET"
                      + " state='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=?,updated_at=?"
                      + " WHERE id=?",
                  token,
                  now().plusSeconds(30),
                  now(),
                  row.get("id"));
              row.put("lease_token", token);
              row.put("attempts", Db.integer(row, "attempts") + 1);
              return row;
            });
    if (task == null) return false;
    String state = "ACCEPTED", reference = null, error = null;
    try {
      var user = db.one("SELECT status FROM user WHERE id=?", task.get("user_id"));
      require(user != null && Db.integer(user, "status") == 1, 409, "ACCOUNT_CLOSED", "账号已停用");
      var order =
          db.one(
              "SELECT number,store_id FROM orders WHERE id=? AND user_id=?",
              task.get("order_id"),
              task.get("user_id"));
      require(order != null, 409, "ORDER_NOT_FOUND", "订单不存在");
      var values =
          Map.of(
              "summary",
              task.get("summary").toString(),
              "orderNumber",
              order.get("number").toString(),
              "time",
              Db.time(task.get("created_at"))
                  .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
      if ("SMS".equals(task.get("channel"))) {
        var phone = db.one("SELECT phone FROM verified_phone WHERE user_id=?", task.get("user_id"));
        require(phone != null, 409, "NO_VERIFIED_PHONE", "用户未绑定手机号");
        var fields = mapping(task);
        var parameters =
            fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> field(values, e.getValue()))
                .toList();
        reference =
            sms.send(phone.get("phone").toString(), task.get("template_id").toString(), parameters);
      } else {
        String openid = identities.openid(Db.id(task, "user_id"), wechat.appId());
        var data = new LinkedHashMap<String, Object>();
        mapping(task).forEach((key, value) -> data.put(key, Map.of("value", field(values, value))));
        // Credential refresh contention happens before any send or subscription consumption.
        String accessToken = wechat.token();
        require(
            db.update(
                    "UPDATE notification_subscription SET credits=credits-1,updated_at=? WHERE"
                        + " user_id=? AND app_id=? AND template_id=? AND credits>0",
                    now(),
                    task.get("user_id"),
                    wechat.appId(),
                    task.get("template_id"))
                == 1,
            409,
            "NO_SUBSCRIPTION",
            "用户未授权该条订阅消息");
        wechat.send(
            accessToken, openid, task.get("template_id").toString(), "pages/orders/index", data);
      }
    } catch (BusinessException ex) {
      error = ex.code();
      state =
          Set.of(
                      "ACCOUNT_CLOSED",
                      "NO_VERIFIED_PHONE",
                      "NO_SUBSCRIPTION",
                      "WECHAT_IDENTITY_REQUIRED")
                  .contains(error)
              ? "SKIPPED"
              : error.endsWith("UNKNOWN") ? "UNKNOWN" : "FAILED";
      if ("WECHAT_TOKEN_BUSY".equals(error) && Db.integer(task, "attempts") < 3)
        state = "REQUESTED";
    } catch (Exception ex) {
      error = "DELIVERY_RESULT_UNKNOWN";
      state = "UNKNOWN";
    }
    db.update(
        "UPDATE notification_delivery SET"
            + " state=?,channel_reference=?,last_error=?,lease_token=NULL,lease_until=NULL,updated_at=?,next_attempt_at=?"
            + " WHERE id=? AND state='PROCESSING' AND lease_token=?",
        state,
        reference,
        error,
        now(),
        now().plusSeconds("REQUESTED".equals(state) ? 10 : 60),
        task.get("id"),
        task.get("lease_token"));
    return true;
  }

  @Scheduled(fixedDelay = 60000)
  public void receipts() {
    if (!enabled() || !environment.getProperty("campus.tasks-enabled", Boolean.class, true)) return;
    operations.run(
        "smsReceipts",
        round -> {
          for (var row :
              db.rows(
                  "SELECT n.*,p.phone FROM notification_delivery n JOIN verified_phone p ON"
                      + " p.user_id=n.user_id WHERE n.state='ACCEPTED' AND n.channel='SMS' AND"
                      + " n.next_attempt_at<=? ORDER BY n.id LIMIT 20",
                  now())) {
            if (!round.withinBudget()) break;
            if (Db.time(row.get("created_at")).isBefore(now().minusDays(7))) {
              db.update(
                  "UPDATE notification_delivery SET"
                      + " state='UNKNOWN',last_error='RECEIPT_EXPIRED',updated_at=? WHERE id=? AND"
                      + " state='ACCEPTED'",
                  now(),
                  row.get("id"));
              continue;
            }
            try {
              String receipt =
                  sms.receipt(
                      row.get("phone").toString(),
                      row.get("channel_reference").toString(),
                      Db.time(row.get("created_at")).atZone(clock.getZone()).toInstant());
              String state =
                  "SUCCESS".equals(receipt)
                      ? "SUCCEEDED"
                      : "FAIL".equals(receipt) ? "FAILED" : "ACCEPTED";
              if ("ACCEPTED".equals(state)
                  && Db.time(row.get("created_at")).isBefore(now().minusDays(7))) state = "UNKNOWN";
              db.update(
                  "UPDATE notification_delivery SET state=?,next_attempt_at=?,updated_at=? WHERE"
                      + " id=? AND state='ACCEPTED'",
                  state,
                  now().plusMinutes(5),
                  now(),
                  row.get("id"));
            } catch (Exception ex) {
              round.failed(ex, row.get("id"));
              db.update(
                  "UPDATE notification_delivery SET"
                      + " next_attempt_at=?,last_error='RECEIPT_UNAVAILABLE',updated_at=? WHERE"
                      + " id=? AND state='ACCEPTED'",
                  now().plusMinutes(5),
                  now(),
                  row.get("id"));
            }
          }
        });
  }

  private Map<String, String> mapping(Map<String, Object> task) {
    String configured = task.get("field_mapping").toString();
    var result = new LinkedHashMap<String, String>();
    json.map(configured).forEach((key, value) -> result.put(key, value.toString()));
    require(!result.isEmpty(), 503, "TEMPLATE_MAPPING_MISSING", "通知模板字段映射尚未配置");
    return result;
  }

  private String field(Map<String, String> values, String name) {
    require(values.containsKey(name), 503, "TEMPLATE_MAPPING_INVALID", "通知模板字段映射不正确");
    return values.get(name);
  }

  private String template(String channel, String event) {
    return environment.getProperty(
        "campus.notifications." + channel.toLowerCase(Locale.ROOT) + ".templates." + event, "");
  }

  private boolean enabled() {
    return environment.getProperty("campus.notifications.enabled", Boolean.class, false);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
