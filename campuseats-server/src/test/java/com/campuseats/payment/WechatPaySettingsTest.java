package com.campuseats.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.campuseats.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class WechatPaySettingsTest {
  @Test
  void localDemoDoesNotRequireOrPretendToHavePaymentCredentials() {
    var settings = new WechatPaySettings(new MockEnvironment());
    assertFalse(settings.enabled);
    assertEquals("", settings.merchantId);
  }

  @Test
  void productionRejectsMockProviderAndMixedDemoProfile() {
    var mock = new MockEnvironment();
    mock.setActiveProfiles("prod");
    assertEquals(
        "PRODUCTION_CONFIG",
        assertThrows(BusinessException.class, () -> new WechatPaySettings(mock)).code());
    var mixed = configured();
    mixed.setActiveProfiles("prod", "demo");
    assertEquals(
        "PRODUCTION_CONFIG",
        assertThrows(BusinessException.class, () -> new WechatPaySettings(mixed)).code());
  }

  @Test
  void productionRejectsEnabledMockFlagWithBooleanCaseInsensitivity() {
    var env = configured().withProperty("campus.mock-payment-enabled", "TRUE");
    env.setActiveProfiles("prod");
    assertEquals(
        "PRODUCTION_CONFIG",
        assertThrows(BusinessException.class, () -> new WechatPaySettings(env)).code());
  }

  @Test
  void completeConfigurationRoutesMiniProgramAndBrowserScenesToTheirOwnAppIds() {
    var env = configured();
    env.setActiveProfiles("prod");
    var settings = new WechatPaySettings(env);
    assertTrue(settings.enabled);
    assertEquals("wx-mini", settings.appId("MINI_PROGRAM"));
    assertEquals("wx-mp", settings.appId("JSAPI"));
    assertEquals("wx-mp", settings.appId("H5"));
    assertEquals("wx-mp", settings.appId("NATIVE"));
  }

  @Test
  void incompleteCredentialsAndHttpCallbacksFailBeforeOpeningAChannel() {
    var noKey = configured().withProperty("campus.payment.api-v3-key", "");
    assertEquals(
        "PAYMENT_CONFIG",
        assertThrows(BusinessException.class, () -> new WechatPaySettings(noKey)).code());
    var http = configured().withProperty("campus.payment.notify-base", "http://example.test");
    assertEquals(
        "PAYMENT_CONFIG",
        assertThrows(BusinessException.class, () -> new WechatPaySettings(http)).code());
  }

  private static MockEnvironment configured() {
    return new MockEnvironment()
        .withProperty("campus.payment.provider", "wechat")
        .withProperty("campus.mock-payment-enabled", "false")
        .withProperty("campus.payment.merchant-id", "1900000100")
        .withProperty("campus.payment.serial", "fixture-serial")
        .withProperty("campus.payment.private-key-path", "fixture-private.pem")
        .withProperty("campus.payment.public-key-path", "fixture-public.pem")
        .withProperty("campus.payment.public-key-id", "PUB_KEY_ID_fixture")
        .withProperty("campus.payment.api-v3-key", "01234567890123456789012345678901")
        .withProperty("campus.payment.mini-app-id", "wx-mini")
        .withProperty("campus.payment.mp-app-id", "wx-mp")
        .withProperty("campus.payment.notify-base", "https://example.test");
  }
}
