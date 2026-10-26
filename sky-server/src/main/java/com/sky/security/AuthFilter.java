package com.sky.security;

import com.sky.common.BusinessException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthFilter extends OncePerRequestFilter {
  private final AuthService auth;
  private final ErrorWriter errors;
  private final RateLimits limits;
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  private final OperationsAccess operations;

  @org.springframework.beans.factory.annotation.Value("${campus.legacy-api:false}")
  private boolean legacyApi;

  @org.springframework.beans.factory.annotation.Value("${campus.rate.search-per-minute:1200}")
  private int searchPerMinute = 1200;

  @org.springframework.beans.factory.annotation.Value("${campus.rate.catalog-per-minute:3000}")
  private int catalogPerMinute = 3000;

  public AuthFilter(
      AuthService auth,
      ErrorWriter errors,
      RateLimits limits,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    this(auth, errors, limits, metrics, new OperationsAccess(""));
  }

  @org.springframework.beans.factory.annotation.Autowired
  public AuthFilter(
      AuthService auth,
      ErrorWriter errors,
      RateLimits limits,
      io.micrometer.core.instrument.MeterRegistry metrics,
      OperationsAccess operations) {
    this.metrics = metrics;
    this.auth = auth;
    this.errors = errors;
    this.limits = limits;
    this.operations = operations;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    req.setAttribute("requestId", requestId);
    res.setHeader("X-Request-Id", requestId);
    try {
      String path = req.getRequestURI();
      if (req.getMethod().equals("GET")) {
        if (path.equals("/api/v1/user/search") || path.equals("/api/v1/user/search/page"))
          limits.check("search-ip:" + req.getRemoteAddr(), searchPerMinute, 60);
        else if (path.equals("/api/v1/user/campuses")
            || path.equals("/api/v1/user/stores")
            || path.equals("/api/v1/user/stores/page")
            || path.matches("/api/v1/user/stores/[0-9]+(?:/(products|categories|reviews))?")
            || path.matches("/api/v1/user/campuses/[0-9]+/buildings"))
          limits.check("catalog-ip:" + req.getRemoteAddr(), catalogPerMinute, 60);
      }
      if (!legacyApi
          && ((path.startsWith("/api/v1/admin/")
                  && !path.startsWith("/api/v1/admin/auth/")
                  && !path.equals("/api/v1/admin/ws-ticket"))
              || path.startsWith("/api/v1/user/menu/")
              || Set.of(
                      "/api/v1/user/shop",
                      "/api/v1/user/buildings",
                      "/api/v1/user/cart",
                      "/api/v1/user/checkout/preview")
                  .contains(path)
              || path.startsWith("/api/v1/user/cart/")
              || path.startsWith("/api/v1/user/orders/by-request/")
              || (path.equals("/api/v1/user/orders") && req.getMethod().equals("POST"))
              || path.matches("/api/v1/user/orders/[^/]+/(pay|cancel)")))
        throw new BusinessException(410, "API_UPGRADED", "单店入口已升级，请刷新页面并选择店铺");
      String token = req.getHeader("Authorization");
      if (token != null && token.startsWith("Bearer ")) {
        Actor actor = auth.authenticate(token.substring(7));
        var authentication =
            new UsernamePasswordAuthenticationToken(
                actor, null, List.of(new SimpleGrantedAuthority("ROLE_" + actor.role())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        if (actor.mustChangePassword()
            && !path.equals("/api/v1/admin/auth/password")
            && !path.equals("/api/v1/admin/auth/me")
            && !path.equals("/api/v1/admin/auth/logout"))
          throw new BusinessException(403, "PASSWORD_CHANGE_REQUIRED", "请先修改初始密码");
        if (!req.getMethod().equals("GET")
            && (path.contains("/orders") || path.contains("/checkouts") || path.contains("/claim")))
          limits.check("order:" + actor.type() + ":" + actor.id(), 30, 60);
      } else {
        Actor maintenance = operations.authenticate(req);
        if (maintenance != null)
          SecurityContextHolder.getContext().setAuthentication(
              new UsernamePasswordAuthenticationToken(maintenance, null,
                  List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
      }
      if (path.endsWith("/auth/login")
          || path.endsWith("/auth/demo")
          || path.endsWith("/auth/wechat")) limits.check("login-ip:" + req.getRemoteAddr(), 20, 60);
      chain.doFilter(req, res);
    } catch (BusinessException ex) {
      errors.write(req, res, ex.status(), ex.code(), ex.getMessage());
    } finally {
      if (req.getMethod().equals("POST")
          && res.getStatus() >= 400
          && (req.getRequestURI().endsWith("/uploads")
              || req.getRequestURI().endsWith("/evidence")))
        metrics
            .counter("campus.upload.rejected", "status", String.valueOf(res.getStatus()))
            .increment();
      if (req.getMethod().equals("POST")
          && Set.of("/api/v1/user/checkouts", "/api/v1/user/orders")
              .contains(req.getRequestURI())) {
        int status = res.getStatus();
        metrics
            .counter(
                "campus.order.requests",
                "api",
                req.getRequestURI().endsWith("/checkouts") ? "checkout" : "legacy",
                "result",
                status < 300 ? "accepted" : status < 500 ? "business_rejected" : "error")
            .increment();
      }
      SecurityContextHolder.clearContext();
    }
  }
}
