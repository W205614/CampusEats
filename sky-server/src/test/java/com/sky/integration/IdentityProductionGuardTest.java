package com.sky.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.sky.common.BusinessException;
import com.sky.infra.Json;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

class IdentityProductionGuardTest {
  private final Json json = new Json(new ObjectMapper());

  private MockEnvironment configured() {
    return new MockEnvironment()
        .withProperty("campus.identity.policy-version", "v1")
        .withProperty("campus.identity.operator", "示例校园运营中心")
        .withProperty("campus.identity.contact", "support@campus.test")
        .withProperty("campus.identity.privacy-text", "运营方已确认的数据处理说明")
        .withProperty("campus.identity.terms-text", "运营方已确认的点餐服务协议")
        .withProperty("campus.identity.phone-enabled", "true")
        .withProperty("campus.identity.oauth-appid", "wx-official")
        .withProperty("campus.identity.oauth-secret", "test-official-secret")
        .withProperty(
            "campus.identity.oauth-redirect-uri",
            "https://campus.test/api/v1/user/auth/wechat/oauth/callback")
        .withProperty("campus.allowed-origins", "https://campus.test")
        .withProperty("campus.wechat-appid", "wx-mini")
        .withProperty("campus.wechat-secret", "test-mini-secret")
        .withProperty("campus.notifications.sms.secret-id", "test-id")
        .withProperty("campus.notifications.sms.secret-key", "test-key")
        .withProperty("campus.notifications.sms.app-id", "1400000000")
        .withProperty("campus.notifications.sms.sign-name", "校园运营")
        .withProperty("campus.notifications.sms.otp-template-id", "template-otp");
  }

  @Test
  void missingPoliciesFailBeforeProductionIdentityCanBeAdvertised() {
    var guard = new IdentityProductionGuard(new MockEnvironment(), json);
    assertEquals(
        "PRODUCTION_IDENTITY", assertThrows(BusinessException.class, () -> guard.run(null)).code());
  }

  @Test
  void validatedSettingsAcceptOnlyTheConfiguredHttpsCallbackOrigin() {
    var env = configured();
    new IdentityProductionGuard(env, json).run(null);
    env.withProperty(
        "campus.identity.oauth-redirect-uri",
        "https://other.test/api/v1/user/auth/wechat/oauth/callback");
    assertEquals(
        "PRODUCTION_OAUTH",
        assertThrows(
                BusinessException.class, () -> new IdentityProductionGuard(env, json).run(null))
            .code());
  }

  @Test
  void notificationsRequireActualTemplateFieldMappings() {
    var env =
        configured()
            .withProperty("campus.notifications.enabled", "true")
            .withProperty("campus.notifications.wechat.templates.ORDER_READY", "template-ready");
    assertThrows(BusinessException.class, () -> new IdentityProductionGuard(env, json).run(null));
    env.withProperty("campus.notifications.wechat.fields.ORDER_READY", "{\"thing1\":\"summary\"}");
    new IdentityProductionGuard(env, json).run(null);
    env.withProperty(
        "campus.notifications.wechat.fields.ORDER_READY", "{\"thing1\":\"fullAddress\"}");
    assertEquals(
        "PRODUCTION_TEMPLATE",
        assertThrows(
                BusinessException.class, () -> new IdentityProductionGuard(env, json).run(null))
            .code());
  }
}
