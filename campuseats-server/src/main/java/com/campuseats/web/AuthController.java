package com.campuseats.web;

import com.campuseats.api.Commands.*;
import com.campuseats.api.Views;
import com.campuseats.common.Hashes;
import com.campuseats.infra.ViewFactory;
import com.campuseats.security.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {
  private final AuthService service;
  private final RateLimits limits;
  private final UserIdentityService identities;

  public AuthController(AuthService service, RateLimits limits, UserIdentityService identities) {
    this.service = service;
    this.limits = limits;
    this.identities = identities;
  }

  @PostMapping("/api/v1/admin/auth/login")
  public Views.SessionView login(@Valid @RequestBody Login input) {
    limits.check(
        "login-account:" + Hashes.sha256(input.username().strip().toLowerCase(Locale.ROOT)),
        10,
        60);
    return ViewFactory.session(service.login(input));
  }

  @PostMapping("/api/v1/user/auth/demo")
  public Views.SessionView demo(@Valid @RequestBody DemoLogin input) {
    return ViewFactory.session(service.demo(input));
  }

  @PostMapping("/api/v1/user/auth/wechat")
  public Views.SessionView wx(@Valid @RequestBody UserIdentityController.WechatLogin input) {
    return ViewFactory.session(identities.miniLogin(input.code(), input.policyVersion()));
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
