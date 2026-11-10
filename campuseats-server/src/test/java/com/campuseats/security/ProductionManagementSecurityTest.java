package com.campuseats.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.*;

import com.campuseats.infra.ProductionManagementSecurity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ProductionManagementSecurityTest {
  @Test
  void privateMonitoringOnlyAllowsReadOnlyProbesWithoutCreatingSessions() throws Exception {
    try (var context = new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.getEnvironment().setActiveProfiles("prod");
      context.register(Config.class);
      context.refresh();
      var mvc =
          webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
      for (String path :
          new String[] {
            "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness",
            "/actuator/prometheus"
          }) {
        var result =
            mvc.perform(get(path).with(request -> { request.setLocalPort(9090); return request; }))
                .andExpect(status().isOk())
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        mvc.perform(post(path).with(request -> { request.setLocalPort(9090); return request; }))
            .andExpect(status().isForbidden());
      }
    }
  }

  @Configuration
  @EnableWebMvc
  @EnableWebSecurity
  @Import(ProductionManagementSecurity.class)
  static class Config {
    @Bean
    Probes probes() {
      return new Probes();
    }
  }

  @RestController
  static class Probes {
    @RequestMapping({
      "/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness",
      "/actuator/prometheus"
    })
    Map<String, String> probe() {
      return Map.of("status", "UP");
    }
  }
}
