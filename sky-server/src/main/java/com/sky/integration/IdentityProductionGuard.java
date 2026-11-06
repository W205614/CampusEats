package com.sky.integration;

import static com.sky.common.BusinessException.require;

import com.sky.infra.Json;
import java.net.URI;
import java.util.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@org.springframework.core.annotation.Order(5)
public class IdentityProductionGuard implements ApplicationRunner {
  private final Environment environment;
  private final Json json;

  public IdentityProductionGuard(Environment environment, Json json) {
    this.environment = environment;
    this.json = json;
  }

  @Override
  public void run(ApplicationArguments args) {
    for (String key :
        List.of(
            "policy-version",
            "operator",
            "contact",
            "privacy-text",
            "terms-text",
            "oauth-appid",
            "oauth-secret",
            "oauth-redirect-uri")) required("campus.identity." + key);
    require(
        required("campus.identity.policy-version").matches("[A-Za-z0-9_.-]{1,40}"),
        503,
        "PRODUCTION_IDENTITY",
        "政策版本须为1至40位字母、数字、点、横线或下划线");
    require(
        environment.getProperty("campus.identity.phone-enabled", Boolean.class, false),
        503,
        "PRODUCTION_IDENTITY",
        "生产H5必须启用手机验证码身份入口");
    for (String key : List.of("secret-id", "secret-key", "app-id", "sign-name", "otp-template-id"))
      required("campus.notifications.sms." + key);
    required("campus.wechat-appid");
    required("campus.wechat-secret");
    URI redirect = URI.create(environment.getProperty("campus.identity.oauth-redirect-uri"));
    require(
        "https".equals(redirect.getScheme())
            && redirect.getUserInfo() == null
            && redirect.getHost() != null
            && "/api/v1/user/auth/wechat/oauth/callback".equals(redirect.getPath())
            && redirect.getQuery() == null
            && redirect.getFragment() == null,
        503,
        "PRODUCTION_OAUTH",
        "公众号回调必须为本站HTTPS身份回调路径");
    String origin =
        "https://"
            + redirect.getHost()
            + (redirect.getPort() == -1 ? "" : ":" + redirect.getPort());
    require(
        Arrays.asList(environment.getProperty("campus.allowed-origins", "").split(","))
            .contains(origin),
        503,
        "PRODUCTION_OAUTH",
        "公众号回调来源必须在生产ALLOWED_ORIGINS中");
    if (environment.getProperty("campus.notifications.enabled", Boolean.class, false)) {
      boolean any = false;
      for (String channel : List.of("sms", "wechat"))
        for (String event : ExternalNotifications.EVENTS) {
          String template =
              environment.getProperty(
                  "campus.notifications." + channel + ".templates." + event, "");
          if (template.isBlank()) continue;
          any = true;
          var fields = json.map(required("campus.notifications." + channel + ".fields." + event));
          require(
              !fields.isEmpty()
                  && fields.values().stream()
                      .allMatch(
                          v ->
                              v instanceof String
                                  && Set.of("summary", "orderNumber", "time").contains(v)),
              503,
              "PRODUCTION_TEMPLATE",
              "通知字段映射只允许summary/orderNumber/time且不能为空");
          require(
              fields.keySet().stream()
                  .allMatch(
                      k ->
                          channel.equals("sms")
                              ? k.matches("[0-9]{2}")
                              : k.matches("[A-Za-z][A-Za-z0-9_]{0,63}")),
              503,
              "PRODUCTION_TEMPLATE",
              "通知模板字段格式不正确");
        }
      require(any, 503, "PRODUCTION_TEMPLATE", "启用外部通知时至少配置一个模板与字段映射");
    }
  }

  private String required(String key) {
    String value = environment.getProperty(key, "");
    require(
        !value.isBlank()
            && !value.contains("replace-with-")
            && !value.contains("部署前填写")
            && !value.contains("实际运营主体名称")
            && !value.contains("实际客服与隐私联系渠道")
            && !value.contains("本机演示维护者"),
        503,
        "PRODUCTION_IDENTITY",
        "生产配置尚未填写: " + key);
    return value;
  }
}
