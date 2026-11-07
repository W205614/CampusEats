package com.campuseats.payment;

import static com.campuseats.common.BusinessException.require;

import com.campuseats.common.BusinessException;
import com.campuseats.infra.Json;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.http.*;
import com.wechat.pay.java.core.notification.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Channel cryptography is exclusively performed by the official APIv3 SDK. */
@Component
public class WechatPayChannel {
  private final WechatPaySettings settings;
  private final Json json;
  private final RSAPublicKeyConfig config;
  private final HttpClient client;
  private final String base;
  private final Clock clock;

  @org.springframework.beans.factory.annotation.Autowired
  public WechatPayChannel(WechatPaySettings settings, Json json, Clock clock) {
    this.settings = settings;
    this.json = json;
    this.clock = clock;
    base = "https://api.mch.weixin.qq.com";
    config =
        settings.enabled
            ? new RSAPublicKeyConfig.Builder()
                .merchantId(settings.merchantId)
                .merchantSerialNumber(settings.serial)
                .privateKeyFromPath(settings.privateKey)
                .publicKeyFromPath(settings.publicKey)
                .publicKeyId(settings.publicKeyId)
                .apiV3Key(settings.apiV3Key)
                .build()
            : null;
    client =
        config == null
            ? null
            : new DefaultHttpClientBuilder()
                .config(config)
                .connectTimeoutMs(2000)
                .readTimeoutMs(5000)
                .writeTimeoutMs(5000)
                .disableRetryOnConnectionFailure()
                .build();
  }

  WechatPayChannel(
      WechatPaySettings settings,
      Json json,
      Clock clock,
      RSAPublicKeyConfig config,
      HttpClient client,
      String base) {
    this.settings = settings;
    this.json = json;
    this.clock = clock;
    this.config = config;
    this.client = client;
    this.base = base;
  }

  public Map<String, Object> request(String method, String path, Map<String, Object> body) {
    require(client != null, 503, "PAYMENT_UNCONFIGURED", "尚未接入微信支付");
    var headers = new HttpHeaders();
    headers.addHeader("Accept", "application/json");
    var response =
        method.equals("GET")
            ? client.get(headers, base + path, Map.class)
            : client.post(
                headers,
                base + path,
                new JsonRequestBody.Builder().body(json.write(body)).build(),
                Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = response.getServiceResponse();
    return data == null ? Map.of() : data;
  }

  public Map<String, Object> prepay(String channel, Map<String, Object> payload) {
    String type =
        switch (channel) {
          case "MINI_PROGRAM", "JSAPI" -> "jsapi";
          case "H5" -> "h5";
          case "NATIVE" -> "native";
          default -> throw new BusinessException(400, "PAYMENT_CHANNEL", "不支持的支付场景");
        };
    var response = request("POST", "/v3/combine-transactions/" + type, payload);
    if (channel.equals("H5")) return Map.of("type", "redirect", "url", text(response, "h5_url"));
    if (channel.equals("NATIVE"))
      return Map.of("type", "qrCode", "url", text(response, "code_url"));
    String timestamp = Long.toString(clock.instant().getEpochSecond()),
        nonce = UUID.randomUUID().toString();
    String packageValue = "prepay_id=" + text(response, "prepay_id"),
        appid = String.valueOf(payload.get("combine_appid"));
    String signature =
        config
            .createSigner()
            .sign(appid + "\n" + timestamp + "\n" + nonce + "\n" + packageValue + "\n")
            .getSign();
    return Map.of(
        "type",
        channel.equals("MINI_PROGRAM") ? "requestPayment" : "wechatBridge",
        "appId",
        appid,
        "timeStamp",
        timestamp,
        "nonceStr",
        nonce,
        "package",
        packageValue,
        "signType",
        "RSA",
        "paySign",
        signature);
  }

  public Map<String, Object> notification(
      String serial, String timestamp, String nonce, String signature, String body) {
    require(config != null, 503, "PAYMENT_UNCONFIGURED", "尚未接入微信支付");
    require(
        serial != null
            && timestamp != null
            && nonce != null
            && signature != null
            && body.length() <= 262144,
        400,
        "INVALID_CALLBACK",
        "回调参数不完整或过大");
    try {
      require(
          Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp)) <= 300,
          401,
          "CALLBACK_EXPIRED",
          "回调已过期");
      @SuppressWarnings("unchecked")
      Map<String, Object> data =
          new NotificationParser(config)
              .parse(
                  new RequestParam.Builder()
                      .serialNumber(serial)
                      .timestamp(timestamp)
                      .nonce(nonce)
                      .signature(signature)
                      .body(body)
                      .build(),
                  Map.class);
      return data;
    } catch (com.wechat.pay.java.core.exception.ValidationException
        | com.wechat.pay.java.core.exception.MalformedMessageException
        | NumberFormatException ex) {
      throw new BusinessException(401, "INVALID_CALLBACK", "支付回调验签或解密失败");
    }
  }

  public byte[] download(String url) throws java.io.IOException {
    var uri = java.net.URI.create(url);
    require(
        "https".equals(uri.getScheme()) && "api.mch.weixin.qq.com".equals(uri.getHost()),
        502,
        "BILL_URL",
        "渠道账单下载地址不正确");
    try (var stream = client.download(url)) {
      byte[] bytes = stream.readNBytes(20 * 1024 * 1024 + 1);
      require(bytes.length <= 20 * 1024 * 1024, 502, "BILL_SIZE", "渠道账单超过20MiB处理上限");
      return bytes;
    }
  }

  public static String text(Map<String, Object> data, String key) {
    require(
        data.get(key) instanceof String s && !s.isBlank(), 502, "CHANNEL_RESPONSE", "渠道响应缺少" + key);
    return (String) data.get(key);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Map<String, Object> data, String key) {
    require(data.get(key) instanceof Map, 502, "CHANNEL_RESPONSE", "渠道响应缺少" + key);
    return (Map<String, Object>) data.get(key);
  }

  public static long amount(Map<String, Object> data, String key) {
    require(data.get(key) instanceof Number, 502, "CHANNEL_RESPONSE", "渠道金额格式错误");
    Number n = (Number) data.get(key);
    require(
        n.doubleValue() == n.longValue() && n.longValue() >= 0,
        502,
        "CHANNEL_RESPONSE",
        "渠道金额格式错误");
    return n.longValue();
  }
}
