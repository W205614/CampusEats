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

  public AuthFilter(
      AuthService auth,
      ErrorWriter errors,
      RateLimits limits,
      io.micrometer.core.instrument.MeterRegistry metrics) {
    this.metrics = metrics;
    this.auth = auth;
    this.errors = errors;
    this.limits = limits;
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
        if (!req.getMethod().equals("GET") && path.contains("/orders"))
          limits.check("order:" + actor.type() + ":" + actor.id(), 30, 60);
      }
      if (path.endsWith("/auth/login")
          || path.endsWith("/auth/demo")
          || path.endsWith("/auth/wechat")) limits.check("login-ip:" + req.getRemoteAddr(), 20, 60);
      chain.doFilter(req, res);
    } catch (BusinessException ex) {
      errors.write(req, res, ex.status(), ex.code(), ex.getMessage());
    } finally {
      if (req.getMethod().equals("POST") && req.getRequestURI().equals("/api/v1/user/orders")) {
        int status = res.getStatus();
        metrics
            .counter(
                "campus.order.requests",
                "result",
                status < 300 ? "accepted" : status < 500 ? "business_rejected" : "error")
            .increment();
      }
      SecurityContextHolder.clearContext();
    }
  }
}
