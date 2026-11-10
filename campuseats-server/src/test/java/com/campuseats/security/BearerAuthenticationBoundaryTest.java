package com.campuseats.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

import com.campuseats.infra.Json;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

class BearerAuthenticationBoundaryTest {
  @Test
  void browserCookiesAndBasicCredentialsCannotAuthorizeMutationsAndUntrustedOriginsAreBlocked()
      throws Exception {
    try (var context = new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.getEnvironment().getPropertySources().addFirst(
          new org.springframework.core.env.MapPropertySource(
              "test", Map.of("campus.allowed-origins", "http://localhost:18083")));
      context.register(Config.class);
      context.refresh();
      var mvc =
          webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
      mvc.perform(post("/api/v1/user/checkouts"))
          .andExpect(status().isUnauthorized());
      mvc.perform(post("/api/v1/user/checkouts").cookie(new Cookie("token", "known")))
          .andExpect(status().isUnauthorized());
      mvc.perform(post("/api/v1/user/checkouts").header("Authorization", "Basic a25vd246a25vd24="))
          .andExpect(status().isUnauthorized());
      mvc.perform(post("/api/v1/user/checkouts").header("Authorization", "Bearer known"))
          .andExpect(status().isOk());
      mvc.perform(
              options("/api/v1/user/checkouts")
                  .header("Origin", "https://attacker.invalid")
                  .header("Access-Control-Request-Method", "POST")
                  .header("Access-Control-Request-Headers", "authorization"))
          .andExpect(status().isForbidden());
    }
  }

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import(SecurityConfiguration.class)
  static class Config {
    @Bean
    AuthService auth() {
      var auth = mock(AuthService.class);
      when(auth.authenticate("known")).thenReturn(new Actor(1, "USER", "USER", "known", false));
      return auth;
    }

    @Bean
    RateLimits limits() {
      return mock(RateLimits.class);
    }

    @Bean
    ErrorWriter errors() {
      return new ErrorWriter(new Json(new ObjectMapper()));
    }

    @Bean
    AuthFilter filter(AuthService auth, ErrorWriter errors, RateLimits limits) {
      return new AuthFilter(auth, errors, limits, new SimpleMeterRegistry());
    }

    @Bean
    ProtectedEndpoint endpoint() {
      return new ProtectedEndpoint();
    }
  }

  @RestController
  static class ProtectedEndpoint {
    @PostMapping("/api/v1/user/checkouts")
    Map<String, String> submit() {
      return Map.of("result", "authenticated");
    }
  }
}
