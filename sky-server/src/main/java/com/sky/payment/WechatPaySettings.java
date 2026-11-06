package com.sky.payment;

import static com.sky.common.BusinessException.require;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class WechatPaySettings {
  public final boolean enabled;
  public final String merchantId, serial, privateKey, publicKey, publicKeyId, apiV3Key;
  public final String miniAppId, mpAppId, notifyBase;

  public WechatPaySettings(Environment e) {
    enabled = "wechat".equals(e.getProperty("campus.payment.provider", "mock"));
    merchantId = value(e, "merchant-id", "WECHATPAY_MCH_ID");
    serial = value(e, "serial", "WECHATPAY_SERIAL");
    privateKey = value(e, "private-key-path", "WECHATPAY_PRIVATE_KEY_PATH");
    publicKey = value(e, "public-key-path", "WECHATPAY_PUBLIC_KEY_PATH");
    publicKeyId = value(e, "public-key-id", "WECHATPAY_PUBLIC_KEY_ID");
    apiV3Key = value(e, "api-v3-key", "WECHATPAY_API_V3_KEY");
    miniAppId = value(e, "mini-app-id", "WECHAT_APPID");
    mpAppId = value(e, "mp-app-id", "WECHAT_MP_APPID");
    notifyBase = value(e, "notify-base", "PUBLIC_BASE_URL");
    if (e.matchesProfiles("prod")) {
      require(!e.matchesProfiles("demo", "trial"), 503, "PRODUCTION_CONFIG", "生产配置不能启用演示或试用模式");
      require(enabled, 503, "PRODUCTION_CONFIG", "生产环境必须配置微信支付");
      require(
          !e.getProperty("campus.mock-payment-enabled", Boolean.class, true),
          503,
          "PRODUCTION_CONFIG",
          "生产必须关闭模拟支付");
    }
    if (enabled) {
      require(
          !merchantId.isBlank()
              && !serial.isBlank()
              && !privateKey.isBlank()
              && !publicKey.isBlank()
              && !publicKeyId.isBlank()
              && apiV3Key.length() == 32
              && !miniAppId.isBlank()
              && !mpAppId.isBlank()
              && notifyBase.startsWith("https://"),
          503,
          "PAYMENT_CONFIG",
          "微信支付商户、公钥、私钥、APIv3密钥、AppID及HTTPS回调地址必须完整配置");
    }
  }

  private String value(Environment e, String key, String env) {
    return e.getProperty("campus.payment." + key, e.getProperty(env, ""));
  }

  public String appId(String channel) {
    return channel.equals("MINI_PROGRAM") ? miniAppId : mpAppId;
  }
}
