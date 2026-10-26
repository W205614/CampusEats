package com.sky.web;

import com.sky.platform.*;
import com.sky.security.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OperationsController {
  private final OperationsService operations;
  private final InboxService inbox;

  public OperationsController(OperationsService operations, InboxService inbox) {
    this.operations = operations;
    this.inbox = inbox;
  }

  public record Availability(boolean enabled) {}

  public record Reason(@NotBlank @Size(max = 200) String reason) {}

  @GetMapping("/merchant/stores/{storeId}/service-policy")
  public OperationsService.Policy policy(@PathVariable long storeId) {
    return operations.policy(storeId);
  }

  @PutMapping("/merchant/stores/{storeId}/service-policy")
  public void policy(
      @PathVariable long storeId, @Valid @RequestBody OperationsService.Policy policy) {
    operations.policy(storeId, policy);
  }

  @GetMapping("/platform/order-exceptions/page")
  public CursorPages.Page<OperationsService.ExceptionView> exceptions(
      @RequestParam(required = false) Long storeId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String state) {
    // The platform URL always requires platform identity even when filtering one store.
    com.sky.common.BusinessException.require(
        Actor.employee().role().equals("PLATFORM_ADMIN"), 403, "FORBIDDEN", "需要平台权限");
    return operations.exceptions(storeId, cursor, limit, state);
  }

  @GetMapping("/merchant/stores/{storeId}/order-exceptions/page")
  public CursorPages.Page<OperationsService.ExceptionView> storeExceptions(
      @PathVariable long storeId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String state) {
    return operations.exceptions(storeId, cursor, limit, state);
  }

  @PostMapping("/order-exceptions/{id}/acknowledge")
  public void acknowledge(
      @PathVariable long id, @Valid @RequestBody OperationsService.Acknowledge input) {
    operations.acknowledge(id, input);
  }

  @PutMapping("/platform/staff/{id}/dispatch-availability")
  public void availability(@PathVariable long id, @RequestBody Availability input) {
    operations.availability(id, input.enabled());
  }

  @PutMapping("/courier/dispatch-availability")
  public void ownAvailability(@RequestBody Availability input) {
    operations.availability(Actor.employee().id(), input.enabled());
  }

  @GetMapping("/courier/dispatch-availability")
  public Map<String, Boolean> ownAvailability() {
    return operations.availability();
  }

  @PostMapping("/platform/staff/{id}/emergency-disable")
  public void emergency(@PathVariable long id, @Valid @RequestBody Reason input) {
    operations.emergency(id, input.reason());
  }

  @GetMapping("/platform/staff/{id}/active-deliveries/page")
  public CursorPages.Page<Map<String, Object>> active(
      @PathVariable long id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return operations.active(id, cursor, limit);
  }

  @GetMapping({"/me/messages/page", "/user/messages/page"})
  public CursorPages.Page<InboxService.Message> messages(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Boolean unread) {
    return inbox.list(cursor, limit, unread);
  }

  @GetMapping({"/me/messages/unread", "/user/messages/unread"})
  public Map<String, String> unread() {
    return Map.of("count", Long.toString(inbox.unread()));
  }

  @PostMapping({"/me/messages/{id}/read", "/user/messages/{id}/read"})
  public void read(@PathVariable long id) {
    inbox.read(id);
  }
}
