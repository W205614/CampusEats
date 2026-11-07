package com.campuseats.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** This listener is published only on the private monitoring address, never via the web proxy. */
@Configuration
@Profile("prod")
public class ProductionManagementSecurity {
  @Bean
  @Order(0)
  SecurityFilterChain privateManagement(
      HttpSecurity http, @Value("${management.server.port:9090}") int port) throws Exception {
    return http.securityMatcher(request -> request.getLocalPort() == port)
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/actuator/health/readiness",
                        "/actuator/health/liveness",
                        "/actuator/prometheus")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
