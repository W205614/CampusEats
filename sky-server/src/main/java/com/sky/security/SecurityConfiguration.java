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
                        "/api/v1/user/auth/login",
                        "/api/v1/user/auth/capabilities",
                        "/api/v1/user/auth/wechat",
                        "/actuator/health",
                        "/actuator/health/readiness",
                        "/actuator/health/liveness",
                        "/api/v1/user/menu/**",
                        "/api/v1/user/shop",
                        "/api/v1/user/buildings",
                        "/uploads/{file:[a-f0-9-]+\\.png}",
                        "/ws/orders")
                    .permitAll()
                    .requestMatchers(
                        org.springframework.http.HttpMethod.GET,
                        "/api/v1/user/campuses",
                        "/api/v1/user/campuses/*/buildings",
                        "/api/v1/user/stores",
                        "/api/v1/user/stores/page",
                        "/api/v1/user/stores/{id:[0-9]+}",
                        "/api/v1/user/stores/*/products",
                        "/api/v1/user/stores/*/categories",
                        "/api/v1/user/stores/*/reviews",
                        "/api/v1/user/search",
                        "/api/v1/user/search/page")
                    .permitAll()
                    .requestMatchers("/docs", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**")
                    .hasAnyRole("ADMIN", "PLATFORM_ADMIN")
                    .requestMatchers("/api/v1/platform/**")
                    .hasRole("PLATFORM_ADMIN")
                    .requestMatchers("/api/v1/merchant/**")
                    .hasAnyRole("PLATFORM_ADMIN", "SHOP_MANAGER", "OPERATOR")
                    .requestMatchers("/api/v1/courier/**")
                    .hasRole("DELIVERER")
                    .requestMatchers("/api/v1/evidence/**")
                    .authenticated()
                    .requestMatchers("/api/v1/admin/**")
                    .hasAnyRole("ADMIN", "PLATFORM_ADMIN", "SHOP_MANAGER", "OPERATOR", "DELIVERER")
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
    c.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
    c.setAllowCredentials(false);
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", c);
    return source;
  }
}
