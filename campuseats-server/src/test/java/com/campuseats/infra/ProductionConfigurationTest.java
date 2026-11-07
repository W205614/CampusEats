package com.campuseats.infra;

import static org.junit.jupiter.api.Assertions.*;

import com.campuseats.common.BusinessException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigurationTest {
  private static final String URL = "jdbc:mysql://db.fixture.invalid/campus?";

  @Test
  void tlsCannotBeEnabledByASuffixOrOverriddenByARepeatedProperty() {
    for (String query :
        List.of(
            "xsslMode=VERIFY_IDENTITY&serverTimezone=Asia/Shanghai",
            "sslMode=VERIFY_IDENTITY&sslMode=DISABLED&serverTimezone=Asia/Shanghai",
            "sslMode=DISABLED&sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Shanghai")) {
      var error =
          assertThrows(
              BusinessException.class,
              () -> ProductionInfrastructureGuard.validateDatabaseUrl(URL + query));
      assertEquals("DATABASE_TLS", error.code());
    }
  }

  @Test
  void hostSpecificOverridesAndAmbiguousPropertyCaseAreRejected() {
    for (String url :
        List.of(
            "jdbc:mysql://(host=db.fixture.invalid,sslMode=DISABLED)/campus?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Shanghai",
            URL + "SSLMode=VERIFY_IDENTITY&serverTimezone=Asia/Shanghai")) {
      var error =
          assertThrows(
              BusinessException.class,
              () -> ProductionInfrastructureGuard.validateDatabaseUrl(url));
      assertEquals("DATABASE_CONFIGURATION", error.code());
    }
  }

  @Test
  void timezoneAliasesCannotOverrideTheDeclaredBusinessTimezone() {
    for (String zone :
        List.of(
            "serverTimezone=Asia/Shanghai&connectionTimeZone=UTC",
            "connectionTimeZone=Asia/Shanghai&serverTimezone=UTC",
            "serverTimezone=Asia/Shanghai&serverTimezone=Asia/Shanghai")) {
      var error =
          assertThrows(
              BusinessException.class,
              () ->
                  ProductionInfrastructureGuard.validateDatabaseUrl(
                      URL + "sslMode=VERIFY_IDENTITY&" + zone));
      assertEquals("DATABASE_TIME_ZONE", error.code());
    }
  }

  @Test
  void encodedParametersAndConsistentTimezoneAliasesAreSupported() {
    ProductionInfrastructureGuard.validateDatabaseUrl(
        URL + "sslMode=VERIFY_IDENTITY&serverTimezone=Asia%2FShanghai");
    ProductionInfrastructureGuard.validateDatabaseUrl(
        URL
            + "sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Shanghai&connectionTimeZone=Asia/Shanghai");
    var error =
        assertThrows(
            BusinessException.class,
            () ->
                ProductionInfrastructureGuard.validateDatabaseUrl(
                    URL + "sslMode=%xy&serverTimezone=Asia/Shanghai"));
    assertEquals("DATABASE_CONFIGURATION", error.code());
  }

  @Test
  void bothProductionRedisFactoriesBoundTheActualSocketAndCommandTimeouts() {
    var env = new MockEnvironment();
    for (String kind : List.of("control", "cache")) {
      env.setProperty("campus.redis." + kind + ".host", kind + ".fixture.invalid");
      env.setProperty("campus.redis." + kind + ".password", "fixture-credential");
    }
    var configuration = new RedisConfiguration.ProductionRedis();
    for (var factory : List.of(configuration.controlRedis(env), configuration.cacheRedis(env))) {
      var client = factory.getClientConfiguration();
      assertTrue(client.isUseSsl());
      assertEquals(Duration.ofSeconds(1), client.getCommandTimeout());
      assertEquals(
          Duration.ofSeconds(1),
          client.getClientOptions().orElseThrow().getSocketOptions().getConnectTimeout());
    }
  }
}
