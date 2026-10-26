package com.sky.web;

import com.sky.api.Commands.Password;
import com.sky.api.Views.SessionView;
import com.sky.common.Hashes;
import com.sky.infra.ViewFactory;
import com.sky.security.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class LocalUserController {
  private final LocalUserService users;
  private final RateLimits limits;
  private final AuthService auth;

  public LocalUserController(LocalUserService users, RateLimits limits, AuthService auth) {
    this.users = users;
    this.limits = limits;
    this.auth = auth;
  }

  @GetMapping("/user/auth/capabilities")
  public AuthService.Capabilities capabilities() {
    return auth.capabilities();
  }

  @PostMapping("/user/auth/login")
  public SessionView login(@Valid @RequestBody LocalUserService.Login input) {
    limits.check(
        "login-account:USER:" + Hashes.sha256(LocalUserService.username(input.username())), 10, 60);
    return ViewFactory.session(users.login(input));
  }

  @PostMapping("/user/auth/password")
  public void password(@Valid @RequestBody Password input) {
    users.change(input);
  }

  @GetMapping("/user/auth/account")
  public LocalUserService.LocalProfile account() {
    return users.profile();
  }

  @GetMapping("/platform/users/page")
  public LocalUserService.Page page(
      @RequestParam(required = false) String query,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return users.page(query, enabled, cursor, limit);
  }

  @PostMapping("/platform/users")
  public LocalUserService.UserView create(@Valid @RequestBody LocalUserService.Create input) {
    return users.create(input);
  }

  @PutMapping("/platform/users/{id}")
  public LocalUserService.UserView update(
      @PathVariable long id, @Valid @RequestBody LocalUserService.Update input) {
    return users.update(id, input);
  }

  @PostMapping("/platform/users/{id}/reset-password")
  public LocalUserService.UserView reset(
      @PathVariable long id, @Valid @RequestBody LocalUserService.Reset input) {
    return users.reset(id, input);
  }
}
