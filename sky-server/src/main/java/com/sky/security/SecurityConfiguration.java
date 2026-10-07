package com.sky.security;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfiguration {
  @Bean
  org.springframework.boot.web.servlet.FilterRegistrationBean<AuthFilter> registration(
      AuthFilter filter) {
    var bean = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
    bean.setEnabled(false);
    return bean;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      AuthFilter filter,
      ErrorWriter errors,
      @Value("${campus.allowed-origins}") String origins)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(cors(origins)))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/admin/auth/login",
                        "/api/v1/user/auth/demo",
                        "/api/v1/user/auth/wechat",
                        "/actuator/health/**",
                        "/api/v1/user/menu/**",
                        "/api/v1/user/shop",
                        "/api/v1/user/buildings",
                        "/uploads/**",
                        "/ws/orders")
                    .permitAll()
                    .requestMatchers("/docs", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/admin/**")
                    .hasAnyRole("ADMIN", "OPERATOR", "DELIVERER")
                    .requestMatchers("/api/v1/user/**")
                    .hasRole("USER")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> errors.write(req, res, 401, "UNAUTHORIZED", "请先登录"))
                    .accessDeniedHandler(
                        (req, res, ex) -> errors.write(req, res, 403, "FORBIDDEN", "没有操作权限")))
        .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  private CorsConfigurationSource cors(String origins) {
    var c = new CorsConfiguration();
    c.setAllowedOrigins(Arrays.asList(origins.split(",")));
    c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    c.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Request-Id"));
    c.setExposedHeaders(List.of("X-Request-Id"));
    c.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
