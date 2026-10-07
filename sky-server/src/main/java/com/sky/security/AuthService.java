package com.sky.security;

import static com.sky.common.BusinessException.require;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import com.sky.api.Commands.*;
import com.sky.common.*;
import com.sky.infra.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class AuthService {
  private final java.util.concurrent.Semaphore passwordSlots =
      new java.util.concurrent.Semaphore(4);
  private final Db db;
  private final Clock clock;
  private final byte[] secret;
  private final RestClient http;
  private final String appid, wxsecret;
  private final org.springframework.core.env.Environment environment;
  private final Argon2PasswordEncoder passwords = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);

  public AuthService(
      Db db,
      Clock clock,
      RestClient http,
      org.springframework.core.env.Environment environment,
      @Value("${campus.jwt-secret}") String secret,
      @Value("${campus.wechat-appid}") String appid,
      @Value("${campus.wechat-secret}") String wxsecret) {
    this.db = db;
    this.clock = clock;
    this.http = http;
    this.environment = environment;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.appid = appid;
    this.wxsecret = wxsecret;
    require(this.secret.length >= 32, 503, "CONFIGURATION", "JWT_ADMIN_SECRET 至少需要32字节，请使用启动脚本");
  }

  @Transactional
  public Map<String, Object> login(Login input) {
    var row = db.one("SELECT * FROM employee WHERE username=? FOR UPDATE", input.username());
    require(
        row != null
            && Db.integer(row, "status") == 1
            && matches(input.password(), String.valueOf(row.get("password"))),
        401,
        "BAD_CREDENTIALS",
        "用户名或密码错误");
    if (!String.valueOf(row.get("password")).startsWith("$argon2"))
      db.update(
          "UPDATE employee SET password=?,must_change_password=true WHERE id=?",
          encodePassword(input.password()),
          row.get("id"));
    return issue("ADMIN", Db.id(row, "id"));
  }

  public String encodePassword(String password) {
    require(passwordSlots.tryAcquire(), 503, "LOGIN_BUSY", "登录繁忙，请稍后重试");
    try {
      return passwords.encode(password);
    } finally {
      passwordSlots.release();
    }
  }

  public boolean matches(String raw, String encoded) {
    if (encoded.startsWith("$argon2")) {
      require(passwordSlots.tryAcquire(), 503, "LOGIN_BUSY", "登录繁忙，请稍后重试");
      try {
        return passwords.matches(raw, encoded);
      } finally {
        passwordSlots.release();
      }
    }
    return encoded.matches("[0-9a-fA-F]{32}")
        && MessageDigest.isEqual(
            encoded.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
            Hashes.digest("MD5", raw).getBytes(StandardCharsets.UTF_8));
  }

  @Transactional
  public Map<String, Object> demo(DemoLogin input) {
    require(environment.matchesProfiles("demo"), 404, "NOT_FOUND", "演示登录未启用");
    String openid = "campus-demo-" + input.account();
    db.update(
        "INSERT INTO user(openid,name,create_time) VALUES(?,?,?) ON DUPLICATE KEY UPDATE"
            + " openid=VALUES(openid)",
        openid,
        "演示用户" + input.account(),
        now());
    return issue("USER", Db.id(db.one("SELECT id FROM user WHERE openid=?", openid), "id"));
  }

  public Map<String, Object> wx(WxLogin input) {
    require(!appid.isBlank() && !wxsecret.isBlank(), 503, "WECHAT_UNCONFIGURED", "尚未配置微信登录");
    Map<?, ?> result;
    try {
      result =
          http.get()
              .uri(
                  "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
                  appid,
                  wxsecret,
                  input.code())
              .retrieve()
              .body(Map.class);
    } catch (Exception ex) {
      throw new BusinessException(503, "WECHAT_UNAVAILABLE", "微信登录暂不可用");
    }
    require(
        result != null && result.get("openid") instanceof String,
        401,
        "WECHAT_LOGIN_FAILED",
        "微信登录失败，请重试");
    String openid = (String) result.get("openid");
    db.update(
        "INSERT INTO user(openid,name,create_time) VALUES(?,'微信用户',?) ON DUPLICATE KEY UPDATE"
            + " openid=VALUES(openid)",
        openid,
        now());
    return issue("USER", Db.id(db.one("SELECT id FROM user WHERE openid=?", openid), "id"));
  }

  public Map<String, Object> issue(String type, long id) {
    var row = subject(type, id);
    require(row != null && Db.integer(row, "status") == 1, 401, "ACCOUNT_DISABLED", "账户不可用");
    String jti = UUID.randomUUID().toString();
    Instant expiry = clock.instant().plus(Duration.ofHours(2));
    db.update(
        "INSERT INTO auth_session(jti,subject_type,subject_id,auth_version,expires_at)"
            + " VALUES(?,?,?,?,?)",
        jti,
        type,
        id,
        row.get("auth_version"),
        LocalDateTime.ofInstant(expiry, clock.getZone()));
    try {
      var claims =
          new JWTClaimsSet.Builder()
              .issuer("campuseats")
              .subject(type + ":" + id)
              .jwtID(jti)
              .issueTime(Date.from(clock.instant()))
              .expirationTime(Date.from(expiry))
              .build();
      var token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      token.sign(new MACSigner(secret));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("token", token.serialize());
      out.put("expiresAt", expiry.toString());
      out.put("id", String.valueOf(id));
      out.put("name", row.get("name"));
      out.put("role", type.equals("USER") ? "USER" : row.get("role"));
      out.put("mustChangePassword", type.equals("ADMIN") && Db.bool(row, "must_change_password"));
      return out;
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public Actor authenticate(String token) {
    try {
      var jwt = SignedJWT.parse(token);
      require(
          jwt.getHeader().getAlgorithm().equals(JWSAlgorithm.HS256)
              && jwt.verify(new MACVerifier(secret)),
          401,
          "UNAUTHORIZED",
          "登录已失效");
      var claims = jwt.getJWTClaimsSet();
      require(
          "campuseats".equals(claims.getIssuer())
              && claims.getExpirationTime() != null
              && claims.getExpirationTime().toInstant().isAfter(clock.instant()),
          401,
          "UNAUTHORIZED",
          "登录已失效");
      var session =
          db.one(
              "SELECT * FROM auth_session WHERE jti=? AND revoked=false AND expires_at>?",
              claims.getJWTID(),
              now());
      require(session != null, 401, "UNAUTHORIZED", "登录已失效");
      String type = String.valueOf(session.get("subject_type"));
      long id = Db.id(session, "subject_id");
      require((type + ":" + id).equals(claims.getSubject()), 401, "UNAUTHORIZED", "登录已失效");
      var row = subject(type, id);
      require(
          row != null
              && Db.integer(row, "status") == 1
              && Db.id(row, "auth_version") == Db.id(session, "auth_version"),
          401,
          "UNAUTHORIZED",
          "登录已失效");
      return new Actor(
          id,
          type,
          type.equals("USER") ? "USER" : String.valueOf(row.get("role")),
          claims.getJWTID(),
          type.equals("ADMIN") && Db.bool(row, "must_change_password"));
    } catch (BusinessException ex) {
      throw ex;
    } catch (org.springframework.dao.DataAccessException ex) {
      throw new BusinessException(503, "DATABASE_UNAVAILABLE", "服务暂不可用");
    } catch (Exception ex) {
      throw new BusinessException(401, "UNAUTHORIZED", "登录已失效");
    }
  }

  public void logout(Actor actor) {
    db.update("UPDATE auth_session SET revoked=true WHERE jti=?", actor.jti());
  }

  @Transactional
  public void change(Actor actor, Password input) {
    var row = db.one("SELECT * FROM employee WHERE id=? FOR UPDATE", actor.id());
    require(
        row != null && matches(input.oldPassword(), String.valueOf(row.get("password"))),
        400,
        "BAD_PASSWORD",
        "原密码错误");
    db.update(
        "UPDATE employee SET password=?,must_change_password=false,auth_version=auth_version+1"
            + " WHERE id=?",
        encodePassword(input.password()),
        actor.id());
    db.update(
        "UPDATE auth_session SET revoked=true WHERE subject_type='ADMIN' AND subject_id=?",
        actor.id());
  }

  private Map<String, Object> subject(String type, long id) {
    if (type.equals("ADMIN")) return db.one("SELECT * FROM employee WHERE id=?", id);
    if (type.equals("USER")) return db.one("SELECT * FROM user WHERE id=?", id);
    return null;
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock);
  }
}
