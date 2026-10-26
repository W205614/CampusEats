package com.sky.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sky.common.BusinessException;
import com.sky.infra.Json;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

class OperationsAccessTest {
  private static final String SECRET = "operations-boundary-test-secret-32-characters";
  private AnnotationConfigWebApplicationContext context;
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    context = new AnnotationConfigWebApplicationContext();
    context.setServletContext(new MockServletContext());
    context.getEnvironment().getPropertySources().addFirst(
        new org.springframework.core.env.MapPropertySource("test", Map.of("campus.allowed-origins", "http://localhost:18083")));
    context.register(Config.class);
    context.refresh();
    mvc = webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
  }

  @AfterEach
  void close() { context.close(); }

  @Test
  void anonymousOnlySeesPublicHealthProbesAndEmployeeRoleCannotReadOperations() throws Exception {
    for (String path : List.of("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness"))
      mvc.perform(get(path)).andExpect(status().isOk());
    mvc.perform(get("/actuator/health/operations")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health/operations").header("Authorization", "Bearer operator"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/health/operations").header("Authorization", "Bearer platform"))
        .andExpect(status().isOk());
  }

  @Test
  void maintenanceCredentialOnlyWorksForExactLoopbackGetAndCannotReplaceBearer() throws Exception {
    String key = key();
    mvc.perform(local(get("/actuator/health/operations")).header("X-Campus-Operations-Key", key))
        .andExpect(status().isOk());
    mvc.perform(local(get("/actuator/health/operations")).header("X-Campus-Operations-Key", "0".repeat(64)))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health/operations").with(req -> { req.setRemoteAddr("192.0.2.4"); return req; })
        .header("X-Campus-Operations-Key", key)).andExpect(status().isUnauthorized());
    mvc.perform(local(get("/actuator/prometheus")).header("X-Campus-Operations-Key", key))
        .andExpect(status().isUnauthorized());
    mvc.perform(local(get("/api/v1/platform/private")).header("X-Campus-Operations-Key", key))
        .andExpect(status().isUnauthorized());
    mvc.perform(local(post("/actuator/health/operations")).header("X-Campus-Operations-Key", key))
        .andExpect(status().isUnauthorized());
    mvc.perform(local(get("/actuator/health/operations")).header("X-Campus-Operations-Key", key)
        .header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    mvc.perform(local(get("/actuator/health/operations")).header("X-Campus-Operations-Key", key)
        .header("Authorization", "Bearer operator")).andExpect(status().isForbidden());
  }

  private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder builder) {
    return builder.with(req -> { req.setRemoteAddr("127.0.0.1"); return req; });
  }

  private static String key() throws Exception {
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal("campuseats:operations-read:v1".getBytes(StandardCharsets.UTF_8)));
  }

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import(SecurityConfiguration.class)
  static class Config {
    @Bean AuthService auth() {
      var auth = mock(AuthService.class);
      when(auth.authenticate("platform")).thenReturn(new Actor(1, "ADMIN", "PLATFORM_ADMIN", "platform", false));
      when(auth.authenticate("operator")).thenReturn(new Actor(2, "ADMIN", "OPERATOR", "operator", false, 1L, 1L));
      when(auth.authenticate("invalid")).thenThrow(new BusinessException(401, "UNAUTHORIZED", "请先登录"));
      return auth;
    }
    @Bean RateLimits limits() { return mock(RateLimits.class); }
    @Bean ErrorWriter errors() { return new ErrorWriter(new Json(new ObjectMapper())); }
    @Bean OperationsAccess operations() { return new OperationsAccess(SECRET); }
    @Bean AuthFilter authFilter(AuthService auth, ErrorWriter errors, RateLimits limits, OperationsAccess operations) {
      return new AuthFilter(auth, errors, limits, new SimpleMeterRegistry(), operations);
    }
    @Bean HealthController health() { return new HealthController(); }
  }

  @RestController
  static class HealthController {
    @GetMapping({"/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness",
        "/actuator/health/operations", "/actuator/prometheus", "/api/v1/platform/private"})
    Map<String, String> health() { return Map.of("status", "UP"); }
  }
}
