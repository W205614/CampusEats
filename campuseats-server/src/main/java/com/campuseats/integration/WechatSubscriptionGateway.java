package com.campuseats.integration;

import static com.campuseats.common.BusinessException.require;

import com.campuseats.common.BusinessException;
import java.time.Duration;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WechatSubscriptionGateway {
  private final RestClient http;
  private final StringRedisTemplate redis;
  private final Environment environment;

  public WechatSubscriptionGateway(
      RestClient http, StringRedisTemplate redis, Environment environment) {
    this.http = http;
    this.redis = redis;
    this.environment = environment;
  }

  public boolean configured() {
    return !appId().isBlank() && !environment.getProperty("campus.wechat-secret", "").isBlank();
  }

  public String appId() {
    return environment.getProperty("campus.wechat-appid", "");
  }

  public void send(
      String accessToken, String openid, String template, String page, Map<String, Object> data) {
    Map<?, ?> result;
    try {
      result =
          http.post()
              .uri(
                  "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token={token}",
                  accessToken)
              .body(
                  Map.of(
                      "touser",
                      openid,
                      "template_id",
                      template,
                      "page",
                      page,
                      "data",
                      data,
                      "miniprogram_state",
                      environment.matchesProfiles("prod") ? "formal" : "trial"))
              .retrieve()
              .body(Map.class);
    } catch (org.springframework.web.client.RestClientException ex) {
      throw new BusinessException(503, "WECHAT_MESSAGE_UNKNOWN", "订阅消息发送结果未知，不自动重复发送");
    }
    require(
        result != null && result.get("errcode") instanceof Number,
        503,
        "WECHAT_MESSAGE_UNKNOWN",
        "订阅消息发送结果未知");
    require(
        ((Number) result.get("errcode")).intValue() == 0,
        503,
        "WECHAT_MESSAGE_REJECTED",
        "微信未受理订阅消息，请检查授权及模板");
  }

  String token() {
    require(configured(), 503, "WECHAT_UNCONFIGURED", "微信订阅消息尚未配置");
    String key = "campus:wechat:access-token:" + appId();
    String old = redis.opsForValue().get(key);
    if (old != null) return old;
    String lock = key + ":refresh";
    require(
        Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock, "1", Duration.ofSeconds(10))),
        503,
        "WECHAT_TOKEN_BUSY",
        "微信凭据正在更新");
    // The short lock expires on its own; an expired owner cannot delete a later owner's lock.
    Map<?, ?> result =
        http.post()
            .uri("https://api.weixin.qq.com/cgi-bin/stable_token")
            .body(
                Map.of(
                    "grant_type",
                    "client_credential",
                    "appid",
                    appId(),
                    "secret",
                    environment.getProperty("campus.wechat-secret", ""),
                    "force_refresh",
                    false))
            .retrieve()
            .body(Map.class);
    require(
        result != null
            && result.get("access_token") instanceof String
            && result.get("expires_in") instanceof Number,
        503,
        "WECHAT_TOKEN_UNAVAILABLE",
        "无法取得微信凭据");
    String value = result.get("access_token").toString();
    long expires = ((Number) result.get("expires_in")).longValue();
    redis.opsForValue().set(key, value, Duration.ofSeconds(Math.max(1, expires - 120)));
    return value;
  }
}
