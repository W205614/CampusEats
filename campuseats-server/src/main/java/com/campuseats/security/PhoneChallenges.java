package com.campuseats.security;

import static com.campuseats.common.BusinessException.require;

import com.campuseats.common.BusinessException;
import com.campuseats.common.Hashes;
import com.campuseats.integration.TencentSmsGateway;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class PhoneChallenges {
  private final StringRedisTemplate redis;
  private final TencentSmsGateway sms;
  private final Environment environment;
  private final SecureRandom random = new SecureRandom();
  private static final DefaultRedisScript<Long> ISSUE =
      new DefaultRedisScript<>(
          """
          if redis.call('EXISTS',KEYS[2])==1 then return 0 end
          local n=redis.call('INCR',KEYS[3]); if n==1 then redis.call('EXPIRE',KEYS[3],86400) end
          if n>10 then return -1 end
          if #KEYS==4 then
            local account=redis.call('INCR',KEYS[4]); if account==1 then redis.call('EXPIRE',KEYS[4],86400) end
            if account>10 then return -3 end
          end
          redis.call('SET',KEYS[2],'1','EX',60)
          redis.call('HSET',KEYS[1],'nonce',ARGV[1],'digest',ARGV[2],'failures','0')
          redis.call('EXPIRE',KEYS[1],300)
          return 1
          """,
          Long.class);
  private static final DefaultRedisScript<Long> VERIFY =
      new DefaultRedisScript<>(
          """
          if redis.call('EXISTS',KEYS[1])==0 then return 0 end
          if redis.call('HGET',KEYS[1],'nonce')~=ARGV[1] then return 0 end
          if tonumber(redis.call('HGET',KEYS[1],'failures'))>=5 then redis.call('DEL',KEYS[1]); return 0 end
          if redis.call('HGET',KEYS[1],'digest')==ARGV[2] then redis.call('DEL',KEYS[1]); return 1 end
          redis.call('HINCRBY',KEYS[1],'failures',1); return 0
          """,
          Long.class);
  private static final DefaultRedisScript<Long> IP_LIMIT =
      new DefaultRedisScript<>(
          """
          local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],3600) end; return n
          """,
          Long.class);

  public PhoneChallenges(
      StringRedisTemplate redis, TencentSmsGateway sms, Environment environment) {
    this.redis = redis;
    this.sms = sms;
    this.environment = environment;
  }

  public boolean enabled() {
    return environment.getProperty("campus.identity.phone-enabled", Boolean.class, false)
        && sms.configured()
        && !template().isBlank();
  }

  public void send(String phone, String purpose, long actor, String address) {
    require(enabled(), 503, "PHONE_LOGIN_UNCONFIGURED", "手机验证码尚未接入");
    String nonce = UUID.randomUUID().toString();
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    String key = key(phone, purpose, actor);
    long admitted;
    try {
      Long ip = redis.execute(IP_LIMIT, List.of("campus:otp:ip:" + Hashes.sha256(address)));
      require(ip != null && ip <= 30, 429, "RATE_LIMITED", "验证码发送过于频繁");
      var quotaKeys =
          new ArrayList<>(
              List.of(
                  key,
                  "campus:otp:cooldown:" + Hashes.sha256(phone),
                  "campus:otp:daily:" + Hashes.sha256(phone)));
      if (actor > 0) quotaKeys.add("campus:otp:account:" + actor);
      Long result =
          redis.execute(ISSUE, quotaKeys, nonce, digest(phone, purpose, actor, nonce, code));
      admitted = result == null ? -2 : result;
    } catch (BusinessException ex) {
      throw ex;
    } catch (org.springframework.dao.DataAccessException ex) {
      throw new BusinessException(503, "CONTROL_UNAVAILABLE", "身份验证服务暂不可用");
    }
    require(admitted != -3, 429, "ACCOUNT_RATE_LIMITED", "当前账号24小时内验证码申请已达10次，请稍后再试");
    require(
        admitted == 1,
        admitted == -2 ? 503 : 429,
        admitted == -2 ? "CONTROL_UNAVAILABLE" : "RATE_LIMITED",
        "请稍后再申请验证码");
    sms.send(phone, template(), List.of(code, "5"));
  }

  public void consume(String phone, String purpose, long actor, String code) {
    require(enabled(), 503, "PHONE_LOGIN_UNCONFIGURED", "手机验证码尚未接入");
    try {
      String key = key(phone, purpose, actor);
      Object nonce = redis.opsForHash().get(key, "nonce");
      Long result =
          nonce == null
              ? 0L
              : redis.execute(
                  VERIFY,
                  List.of(key),
                  nonce.toString(),
                  digest(phone, purpose, actor, nonce.toString(), code));
      require(Long.valueOf(1).equals(result), 400, "OTP_INVALID", "验证码错误、已使用或已过期");
    } catch (BusinessException ex) {
      throw ex;
    } catch (org.springframework.dao.DataAccessException ex) {
      throw new BusinessException(503, "CONTROL_UNAVAILABLE", "身份验证服务暂不可用");
    }
  }

  private String key(String phone, String purpose, long actor) {
    return "campus:otp:" + purpose + ":" + actor + ":" + Hashes.sha256(phone);
  }

  private String template() {
    return environment.getProperty("campus.notifications.sms.otp-template-id", "");
  }

  private String digest(String phone, String purpose, long actor, String nonce, String code) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              environment.getRequiredProperty("campus.jwt-secret").getBytes(StandardCharsets.UTF_8),
              "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal(
                  (phone + "|" + purpose + "|" + actor + "|" + nonce + "|" + code)
                      .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("Cannot hash phone challenge", ex);
    }
  }
}
