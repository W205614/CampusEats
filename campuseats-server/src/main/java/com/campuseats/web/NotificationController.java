package com.campuseats.web;

import com.campuseats.integration.ExternalNotifications;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
  public record Subscription(@NotBlank @Size(max = 128) String templateId, boolean accepted) {}

  private final ExternalNotifications notifications;

  public NotificationController(ExternalNotifications notifications) {
    this.notifications = notifications;
  }

  @GetMapping("/user/notifications/templates")
  public ExternalNotifications.SubscriptionTemplates templates() {
    return notifications.templates();
  }

  @PostMapping("/user/notifications/subscriptions")
  public void subscribe(@Valid @RequestBody Subscription input) {
    notifications.authorize(input.templateId(), input.accepted());
  }

  @GetMapping("/user/notifications/deliveries")
  public List<Map<String, Object>> user(
      @RequestParam(required = false) Long beforeId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String state) {
    return notifications.deliveries(beforeId, limit, false, state);
  }

  @GetMapping("/platform/notifications/deliveries")
  public List<Map<String, Object>> platform(
      @RequestParam(required = false) Long beforeId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String state) {
    return notifications.deliveries(beforeId, limit, true, state);
  }
}
