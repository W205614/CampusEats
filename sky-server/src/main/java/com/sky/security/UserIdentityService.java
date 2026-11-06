package com.sky.security;

import static com.sky.common.BusinessException.require;

import com.sky.common.BusinessException;
import com.sky.infra.Db;
import com.sky.infra.Json;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/**
 * App-scoped external identities never identify accounts by nickname or a client-supplied openid.
 */
@Service
public class UserIdentityService {
  public record Capabilities(
      boolean phoneLoginEnabled,
      boolean oauthEnabled,
      String policyVersion,
      String privacyUrl,
      String termsUrl) {}

  public record Profile(
      long id,
      String name,
      String phone,
      boolean miniProgramBound,
      boolean officialAccountBound,
      String closureState) {}

  public record Policy(
      String version, String operator, String contact, String privacyText, String termsText) {}

  private final Db db;
  private final AuthService auth;
  private final PhoneChallenges challenges;
  private final StringRedisTemplate redis;
  private final RestClient http;
  private final Environment environment;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final Json json;

  public UserIdentityService(
      Db db,
      AuthService auth,
      PhoneChallenges challenges,
      StringRedisTemplate redis,
      RestClient http,
      Environment environment,
      Clock clock,
      PlatformTransactionManager manager,
      Json json) {
    this.db = db;
    this.auth = auth;
    this.challenges = challenges;
    this.redis = redis;
    this.http = http;
    this.environment = environment;
    this.clock = clock;
    this.tx = new TransactionTemplate(manager);
    this.json = json;
  }

  public Capabilities capabilities() {
    return new Capabilities(
        challenges.enabled(),
        oauthEnabled(),
        policyVersion(),
        environment.getProperty("campus.identity.privacy-url", "/app/pages/privacy/index"),
        environment.getProperty("campus.identity.terms-url", "/app/pages/privacy/index"));
  }

  public Policy policy() {
    return new Policy(
        policyVersion(),
        environment.getProperty("campus.identity.operator", "本机演示维护者"),
        environment.getProperty("campus.identity.contact", "请联系当前部署的维护者"),
        environment.getProperty(
            "campus.identity.privacy-text",
            "我们仅为登录、点餐、配送与售后处理手机号、微信应用身份、收货地址和交易记录。配送信息仅向当前履约人员提供；账号可在个人资料申请注销，交易和审计记录按运营方保留策略保存。第三方短信、微信及存储服务仅用于相应业务；本机演示请使用虚构资料。"),
        environment.getProperty(
            "campus.identity.terms-text",
            "请核对校区、店铺、收货地址、商品和实付金额后提交订单。各店独立履约，跨店联合优惠由参与店铺承担。配送中或备餐中的问题通过订单售后和平台介入处理。付款结果以服务端确认状态为准，处理中请勿重复下单。演示模式不产生真实资金交易。"));
  }

  public Map<String, Object> miniLogin(String code) {
    return miniLogin(code, null);
  }

  public Map<String, Object> miniLogin(String code, String policy) {
    require(
        !environment.matchesProfiles("prod") || policyVersion().equals(policy),
        400,
        "CONSENT_REQUIRED",
        "请阅读并同意当前隐私政策和用户协议");
    String app = miniAppid();
    String secret = environment.getProperty("campus.wechat-secret", "");
    require(!app.isBlank() && !secret.isBlank(), 503, "WECHAT_UNCONFIGURED", "尚未配置微信登录");
    var result =
        exchange(
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
            app,
            secret,
            code);
    String subject = subject(result);
    return tx.execute(
        status -> {
          var identity =
              db.one(
                  "SELECT user_id FROM external_identity WHERE provider='WECHAT' AND app_id=? AND"
                      + " subject=?",
                  app,
                  subject);
          long user;
          if (identity == null) {
            var legacy =
                app.equals(environment.getProperty("campus.identity.legacy-wechat-appid", ""))
                    ? db.one("SELECT id FROM user WHERE openid=?", subject)
                    : null;
            user =
                legacy == null
                    ? db.insert("INSERT INTO user(name,create_time) VALUES('微信用户',?)", now())
                    : Db.id(legacy, "id");
            bind(user, app, subject);
          } else user = Db.id(identity, "user_id");
          if (policyVersion().equals(policy))
            db.update(
                "INSERT IGNORE INTO privacy_consent(user_id,policy_version,accepted_at)"
                    + " VALUES(?,?,?)",
                user,
                policy,
                now());
          return auth.issue("USER", user);
        });
  }

  public void sendCode(String phone, String purpose, String source) {
    require(Set.of("LOGIN", "BIND", "CLOSE").contains(purpose), 400, "INVALID_PURPOSE", "验证码用途不正确");
    long user = "LOGIN".equals(purpose) ? 0 : Actor.user().id();
    if ("CLOSE".equals(purpose)) {
      var row = db.one("SELECT phone FROM verified_phone WHERE user_id=?", user);
      require(row != null && phone.equals(row.get("phone")), 400, "PHONE_MISMATCH", "请使用当前绑定的手机号");
    }
    challenges.send(phone, purpose, user, source);
  }

  public Map<String, Object> phoneLogin(String phone, String code, String policy) {
    require(policyVersion().equals(policy), 400, "CONSENT_REQUIRED", "请阅读并同意当前隐私政策和用户协议");
    challenges.consume(phone, "LOGIN", 0, code);
    return tx.execute(
        status -> {
          var account =
              db.one("SELECT user_id FROM verified_phone WHERE phone=? FOR UPDATE", phone);
          long user;
          if (account == null) {
            user =
                db.insert(
                    "INSERT INTO user(name,phone,create_time) VALUES(?,?,?)",
                    "用户" + phone.substring(7),
                    phone,
                    now());
            db.update(
                "INSERT INTO verified_phone(phone,user_id,verified_at) VALUES(?,?,?)",
                phone,
                user,
                now());
          } else user = Db.id(account, "user_id");
          db.update(
              "INSERT IGNORE INTO privacy_consent(user_id,policy_version,accepted_at)"
                  + " VALUES(?,?,?)",
              user,
              policy,
              now());
          return auth.issue("USER", user);
        });
  }

  public void bindPhone(String phone, String code) {
    Actor actor = Actor.user();
    long user = actor.id();
    challenges.consume(phone, "BIND", user, code);
    tx.executeWithoutResult(
        status -> {
          lockUser(user, actor.jti());
          var existing =
              db.one("SELECT user_id FROM verified_phone WHERE phone=? FOR UPDATE", phone);
          require(
              existing == null || Db.id(existing, "user_id") == user,
              409,
              "PHONE_ALREADY_BOUND",
              "手机号已绑定其他账号，请登录原账号；历史交易不会自动合并");
          var own = db.one("SELECT phone FROM verified_phone WHERE user_id=? FOR UPDATE", user);
          require(
              own == null || phone.equals(own.get("phone")),
              409,
              "PHONE_ALREADY_BOUND",
              "账号已有绑定手机号，请联系平台处理换绑");
          if (existing == null) {
            try {
              db.update(
                  "INSERT INTO verified_phone(phone,user_id,verified_at) VALUES(?,?,?)",
                  phone,
                  user,
                  now());
            } catch (DuplicateKeyException ex) {
              var conflict =
                  db.one(
                      "SELECT user_id,phone FROM verified_phone WHERE phone=? OR user_id=?",
                      phone,
                      user);
              if (conflict != null
                  && (Db.id(conflict, "user_id") != user || !phone.equals(conflict.get("phone"))))
                throw new BusinessException(
                    409, "PHONE_ALREADY_BOUND", "手机号或账号已有绑定，请登录原账号；历史交易不会自动合并");
              throw ex;
            }
          }
          db.update("UPDATE user SET phone=? WHERE id=?", phone, user);
        });
  }

  public void bindMiniProgram(String code) {
    Actor actor = Actor.user();
    long user = actor.id();
    var result =
        exchange(
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
            miniAppid(),
            environment.getProperty("campus.wechat-secret", ""),
            code);
    tx.executeWithoutResult(
        status -> {
          lockUser(user, actor.jti());
          bind(user, miniAppid(), subject(result));
        });
  }

  /** Payments must use the payer identity issued for the exact appid sent to WeChat. */
  public String openid(long user, String appid) {
    var row =
        db.one(
            "SELECT subject FROM external_identity WHERE user_id=? AND provider='WECHAT' AND"
                + " app_id=?",
            user,
            appid);
    require(row != null, 409, "WECHAT_IDENTITY_REQUIRED", "请先为当前付款场景绑定微信身份");
    return row.get("subject").toString();
  }

  public Map<String, String> startOauth() {
    Actor actor = Actor.user();
    require(oauthEnabled(), 503, "WECHAT_OAUTH_UNCONFIGURED", "公众号授权尚未配置");
    String state = UUID.randomUUID().toString();
    control(
        () -> {
          redis
              .opsForValue()
              .set(
                  "campus:oauth:" + state,
                  json.write(Map.of("userId", actor.id(), "jti", actor.jti())),
                  Duration.ofMinutes(5));
          return true;
        });
    String url =
        "https://open.weixin.qq.com/connect/oauth2/authorize?appid="
            + encode(oauthAppid())
            + "&redirect_uri="
            + encode(environment.getRequiredProperty("campus.identity.oauth-redirect-uri"))
            + "&response_type=code&scope=snsapi_base&state="
            + state
            + "#wechat_redirect";
    return Map.of("url", url);
  }

  public String completeOauth(String state, String code) {
    require(state.matches("[a-f0-9-]{36}"), 400, "OAUTH_STATE_INVALID", "授权请求已过期");
    String saved = control(() -> redis.opsForValue().getAndDelete("campus:oauth:" + state));
    require(saved != null, 400, "OAUTH_STATE_INVALID", "授权请求已使用或已过期");
    var pending = json.map(saved);
    long user = ((Number) pending.get("userId")).longValue();
    String jti = pending.get("jti").toString();
    require(validSession(user, jti), 401, "UNAUTHORIZED", "授权会话已失效");
    var result =
        exchange(
            "https://api.weixin.qq.com/sns/oauth2/access_token?appid={appid}&secret={secret}&code={code}&grant_type=authorization_code",
            oauthAppid(),
            environment.getProperty("campus.identity.oauth-secret", ""),
            code);
    tx.executeWithoutResult(
        status -> {
          lockUser(user, jti);
          bind(user, oauthAppid(), subject(result));
        });
    return "/app/pages/orders/index?wechat=bound";
  }

  public Profile profile() {
    long user = Actor.user().id();
    var row =
        db.one(
            "SELECT u.name,p.phone,c.state FROM user u LEFT JOIN verified_phone p ON p.user_id=u.id"
                + " LEFT JOIN account_closure c ON c.user_id=u.id WHERE u.id=?",
            user);
    String phone = row.get("phone") == null ? null : row.get("phone").toString();
    return new Profile(
        user,
        Objects.toString(row.get("name"), ""),
        phone == null ? null : phone.substring(0, 3) + "****" + phone.substring(7),
        bound(user, miniAppid()),
        bound(user, oauthAppid()),
        Objects.toString(row.get("state"), "NONE"));
  }

  public void updateName(String name) {
    Actor actor = Actor.user();
    long user = actor.id();
    tx.executeWithoutResult(
        status -> {
          lockUser(user, actor.jti());
          db.update("UPDATE user SET name=? WHERE id=?", name.strip(), user);
        });
  }

  public void consent(String version) {
    Actor actor = Actor.user();
    long user = actor.id();
    require(policyVersion().equals(version), 400, "CONSENT_REQUIRED", "隐私政策版本已变化，请重新阅读");
    tx.executeWithoutResult(
        status -> {
          lockUser(user, actor.jti());
          db.update(
              "INSERT IGNORE INTO privacy_consent(user_id,policy_version,accepted_at)"
                  + " VALUES(?,?,?)",
              user,
              version,
              now());
        });
  }

  public void close(String phone, String code) {
    Actor actor = Actor.user();
    long user = actor.id();
    var verified = db.one("SELECT phone FROM verified_phone WHERE user_id=?", user);
    require(
        verified != null && phone.equals(verified.get("phone")),
        400,
        "PHONE_MISMATCH",
        "请先绑定手机号再申请注销");
    challenges.consume(phone, "CLOSE", user, code);
    tx.executeWithoutResult(
        status -> {
          lockUser(user, actor.jti());
          require(
              db.count("SELECT COUNT(*) FROM orders WHERE user_id=? AND status NOT IN(5,6)", user)
                      == 0
                  && db.count(
                          "SELECT COUNT(*) FROM aftersale WHERE user_id=? AND state"
                              + " IN('PENDING','ESCALATED','APPROVED')",
                          user)
                      == 0
                  && db.count(
                          "SELECT COUNT(*) FROM refund_request r JOIN orders o ON o.id=r.order_id"
                              + " WHERE o.user_id=? AND r.state<>'SUCCEEDED'",
                          user)
                      == 0
                  && db.count(
                          "SELECT COUNT(*) FROM refund_task r JOIN orders o ON o.id=r.order_id"
                              + " WHERE o.user_id=? AND r.state<>'SUCCEEDED'",
                          user)
                      == 0,
              409,
              "ACCOUNT_HAS_ACTIVE_BUSINESS",
              "请先处理未完成订单、售后和退款");
          db.update(
              "INSERT INTO account_closure(user_id,state,requested_at,completed_at)"
                  + " VALUES(?,'COMPLETED',?,?)",
              user,
              now(),
              now());
          db.update(
              "UPDATE user SET"
                  + " name='已注销用户',phone=NULL,openid=NULL,avatar=NULL,id_number=NULL,sex=NULL,status=0,default_address_id=NULL,auth_version=auth_version+1"
                  + " WHERE id=?",
              user);
          db.update(
              "UPDATE address_book SET consignee='已注销',phone='',detail='' WHERE user_id=?", user);
          db.update(
              "UPDATE auth_session SET revoked=true WHERE subject_type='USER' AND subject_id=?",
              user);
          db.update("DELETE FROM external_identity WHERE user_id=?", user);
          db.update("DELETE FROM verified_phone WHERE user_id=?", user);
          db.update("DELETE FROM notification_subscription WHERE user_id=?", user);
          db.update(
              "UPDATE notification_delivery SET"
                  + " state='SKIPPED',last_error='ACCOUNT_CLOSED',updated_at=? WHERE user_id=? AND"
                  + " state IN('REQUESTED','ACCEPTED')",
              now(),
              user);
        });
  }

  private void bind(long user, String app, String subject) {
    var old =
        db.one(
            "SELECT user_id,subject FROM external_identity WHERE provider='WECHAT' AND app_id=? AND"
                + " (subject=? OR user_id=?) FOR UPDATE",
            app,
            subject,
            user);
    require(
        old == null || (Db.id(old, "user_id") == user && subject.equals(old.get("subject"))),
        409,
        "WECHAT_ALREADY_BOUND",
        "微信身份已绑定其他账号，请使用原账号；不会自动合并历史交易");
    if (old != null) return;
    try {
      db.update(
          "INSERT INTO external_identity(user_id,provider,app_id,subject,created_at)"
              + " VALUES(?,'WECHAT',?,?,?)",
          user,
          app,
          subject,
          now());
    } catch (DuplicateKeyException ex) {
      var conflict =
          db.one(
              "SELECT user_id,subject FROM external_identity WHERE provider='WECHAT' AND app_id=?"
                  + " AND (subject=? OR user_id=?)",
              app,
              subject,
              user);
      if (conflict != null
          && (Db.id(conflict, "user_id") != user || !subject.equals(conflict.get("subject"))))
        throw new BusinessException(409, "WECHAT_ALREADY_BOUND", "微信身份已绑定其他账号，请使用原账号；不会自动合并历史交易");
      throw ex;
    }
  }

  private Map<?, ?> exchange(String uri, String app, String secret, String code) {
    require(!app.isBlank() && !secret.isBlank(), 503, "WECHAT_UNCONFIGURED", "微信身份服务尚未配置");
    try {
      Map<?, ?> value = http.get().uri(uri, app, secret, code).retrieve().body(Map.class);
      require(
          value != null && value.get("openid") instanceof String,
          401,
          "WECHAT_LOGIN_FAILED",
          "微信授权失败，请重新发起");
      return value;
    } catch (org.springframework.web.client.RestClientException ex) {
      throw new BusinessException(503, "WECHAT_UNAVAILABLE", "微信身份服务暂不可用");
    }
  }

  private String subject(Map<?, ?> result) {
    String subject = result.get("openid").toString();
    require(subject.matches("[A-Za-z0-9_-]{1,128}"), 503, "WECHAT_IDENTITY_INVALID", "微信身份响应格式不正确");
    return subject;
  }

  private boolean bound(long user, String app) {
    return !app.isBlank()
        && db.count(
                "SELECT COUNT(*) FROM external_identity WHERE user_id=? AND provider='WECHAT' AND"
                    + " app_id=?",
                user,
                app)
            > 0;
  }

  /** Uses the same user-then-session lock order as account revocation and closure. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void requireCurrentUser(Actor actor) {
    lockUser(actor.id(), actor.jti());
  }

  private void lockUser(long user, String jti) {
    var account =
        db.one("SELECT id,auth_version FROM user WHERE id=? AND status=1 FOR UPDATE", user);
    require(account != null, 401, "ACCOUNT_DISABLED", "账号已停用");
    var session =
        db.one(
            "SELECT subject_type,subject_id,auth_version FROM auth_session WHERE jti=? AND"
                + " revoked=false AND expires_at>? FOR UPDATE",
            jti,
            now());
    require(
        session != null
            && "USER".equals(session.get("subject_type"))
            && Db.id(session, "subject_id") == user
            && Db.id(session, "auth_version") == Db.id(account, "auth_version"),
        401,
        "UNAUTHORIZED",
        "登录已失效，请重新登录");
  }

  private boolean validSession(long user, String jti) {
    return db.count(
            "SELECT COUNT(*) FROM auth_session s JOIN user u ON u.id=s.subject_id WHERE s.jti=? AND"
                + " s.subject_type='USER' AND s.subject_id=? AND s.revoked=false AND s.expires_at>?"
                + " AND u.status=1 AND u.auth_version=s.auth_version",
            jti,
            user,
            now())
        == 1;
  }

  private boolean oauthEnabled() {
    return !oauthAppid().isBlank()
        && !environment.getProperty("campus.identity.oauth-secret", "").isBlank()
        && environment.getProperty("campus.identity.oauth-redirect-uri", "").startsWith("https://");
  }

  private String miniAppid() {
    return environment.getProperty("campus.wechat-appid", "");
  }

  private String oauthAppid() {
    return environment.getProperty("campus.identity.oauth-appid", "");
  }

  private String policyVersion() {
    return environment.getProperty("campus.identity.policy-version", "2026-11-06");
  }

  private <T> T control(java.util.function.Supplier<T> operation) {
    try {
      return operation.get();
    } catch (org.springframework.dao.DataAccessException ex) {
      throw new BusinessException(503, "CONTROL_UNAVAILABLE", "身份验证服务暂不可用");
    }
  }

  private static String encode(String input) {
    return URLEncoder.encode(input, StandardCharsets.UTF_8);
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
