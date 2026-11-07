package com.campuseats.web;

import com.campuseats.api.Views.SessionView;
import com.campuseats.infra.ViewFactory;
import com.campuseats.security.UserIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
public class UserIdentityController {
  public record SendCode(
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
      @NotBlank @Pattern(regexp = "LOGIN|BIND|CLOSE") String purpose) {}

  public record PhoneCode(
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}

  public record PhoneLogin(
      @NotBlank @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
      @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
      @NotBlank @Size(max = 40) String policyVersion) {}

  public record Name(@NotBlank @Size(max = 32) String name) {}

  public record Consent(@NotBlank @Size(max = 40) String version) {}

  public record WechatCode(@NotBlank @Size(max = 256) String code) {}

  public record WechatLogin(
      @NotBlank @Size(max = 256) String code, @Size(max = 40) String policyVersion) {}

  private final UserIdentityService identities;

  public UserIdentityController(UserIdentityService identities) {
    this.identities = identities;
  }

  @GetMapping("/auth/identity-capabilities")
  public UserIdentityService.Capabilities capabilities() {
    return identities.capabilities();
  }

  @GetMapping("/auth/privacy-policy")
  public UserIdentityService.Policy policy() {
    return identities.policy();
  }

  @PostMapping("/auth/otp/send")
  public Map<String, Integer> send(@Valid @RequestBody SendCode input, HttpServletRequest request) {
    identities.sendCode(input.phone(), input.purpose(), request.getRemoteAddr());
    return Map.of("expiresIn", 300, "retryAfter", 60);
  }

  @PostMapping("/auth/otp/login")
  public SessionView login(@Valid @RequestBody PhoneLogin input) {
    return ViewFactory.session(
        identities.phoneLogin(input.phone(), input.code(), input.policyVersion()));
  }

  @PostMapping("/auth/phone/bind")
  public void bindPhone(@Valid @RequestBody PhoneCode input) {
    identities.bindPhone(input.phone(), input.code());
  }

  @PostMapping("/auth/wechat/bind")
  public void bindWechat(@Valid @RequestBody WechatCode input) {
    identities.bindMiniProgram(input.code());
  }

  @PostMapping("/auth/wechat/oauth/start")
  public Map<String, String> start() {
    return identities.startOauth();
  }

  @GetMapping("/auth/wechat/oauth/callback")
  public ResponseEntity<Void> callback(
      @RequestParam @Size(max = 40) String state, @RequestParam @Size(max = 256) String code) {
    return ResponseEntity.status(302)
        .location(URI.create(identities.completeOauth(state, code)))
        .build();
  }

  @GetMapping("/profile")
  public UserIdentityService.Profile profile() {
    return identities.profile();
  }

  @PutMapping("/profile")
  public void profile(@Valid @RequestBody Name input) {
    identities.updateName(input.name());
  }

  @PostMapping("/privacy-consents")
  public void consent(@Valid @RequestBody Consent input) {
    identities.consent(input.version());
  }

  @PostMapping("/account-closure")
  public void close(@Valid @RequestBody PhoneCode input) {
    identities.close(input.phone(), input.code());
  }
}
