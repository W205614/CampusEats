package com.sky.web;

import com.sky.api.Commands.*;
import com.sky.api.Views;
import com.sky.common.Hashes;
import com.sky.infra.ViewFactory;
import com.sky.security.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
  private final AuthService service;
  private final RateLimits limits;

  public AuthController(AuthService service, RateLimits limits) {
    this.service = service;
    this.limits = limits;
  }

  @PostMapping("/api/v1/admin/auth/login")
  public Views.SessionView login(@Valid @RequestBody Login input) {
    limits.check("login-account:" + Hashes.sha256(input.username()), 10, 60);
    return ViewFactory.session(service.login(input));
  }

  @PostMapping("/api/v1/user/auth/demo")
  public Views.SessionView demo(@Valid @RequestBody DemoLogin input) {
    return ViewFactory.session(service.demo(input));
  }

  @PostMapping("/api/v1/user/auth/wechat")
  public Views.SessionView wx(@Valid @RequestBody WxLogin input) {
    return ViewFactory.session(service.wx(input));
  }

  @GetMapping({"/api/v1/admin/auth/me", "/api/v1/user/auth/me"})
  public Actor me() {
    return Actor.current();
  }

  @PostMapping({"/api/v1/admin/auth/logout", "/api/v1/user/auth/logout"})
  public void logout() {
    service.logout(Actor.current());
  }

  @PostMapping("/api/v1/admin/auth/password")
  public void password(@Valid @RequestBody Password input) {
    service.change(Actor.employee(), input);
  }
}
