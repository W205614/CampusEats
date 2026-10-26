package com.sky.web;

import com.sky.api.Commands;
import com.sky.platform.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "campus.legacy-api",
    havingValue = "false",
    matchIfMissing = true)
@RequestMapping("/api/v1")
public class PlatformFulfillmentController {
  private final DirectoryService directory;
  private final FulfillmentService fulfillment;

  public PlatformFulfillmentController(DirectoryService directory, FulfillmentService fulfillment) {
    this.directory = directory;
    this.fulfillment = fulfillment;
  }

  @PostMapping("/user/orders/{id}/reminder")
  public void reminder(@PathVariable long id) {
    fulfillment.reminder(id);
  }

  @GetMapping("/merchant/stores/{storeId}/couriers")
  public Object couriers(@PathVariable long storeId) {
    return directory.couriers(storeId);
  }

  @GetMapping("/user/orders")
  public List<PlatformViews.OrderResponse> userOrders(
      @RequestParam(required = false) Long beforeId,
      @RequestParam(required = false) Integer limit) {
    return PlatformViewFactory.orders(fulfillment.orders(null, beforeId, limit));
  }

  @GetMapping("/merchant/stores/{storeId}/orders")
  public List<PlatformViews.MerchantOrderResponse> storeOrders(
      @PathVariable long storeId,
      @RequestParam(required = false) Long beforeId,
      @RequestParam(required = false) Integer limit) {
    return PlatformViewFactory.merchantOrders(fulfillment.orders(storeId, beforeId, limit));
  }

  @GetMapping("/courier/orders")
  public List<PlatformViews.CourierOrderResponse> courierOrders(
      @RequestParam(required = false) Long beforeId,
      @RequestParam(required = false) Integer limit) {
    return PlatformViewFactory.courierOrders(fulfillment.orders(null, beforeId, limit));
  }

  @GetMapping(value = "/merchant/orders/{id}")
  @io.swagger.v3.oas.annotations.Operation(operationId = "order")
  public PlatformViews.MerchantOrderResponse order(@PathVariable long id) {
    return PlatformViewFactory.merchantOrder(fulfillment.detail(id));
  }

  @GetMapping(value = "/courier/orders/{id}")
  @io.swagger.v3.oas.annotations.Operation(operationId = "order_1")
  public PlatformViews.CourierOrderResponse courierOrder(@PathVariable long id) {
    return PlatformViewFactory.courierOrder(fulfillment.detail(id));
  }

  @GetMapping(value = "/user/orders/{id}")
  @io.swagger.v3.oas.annotations.Operation(operationId = "order_2")
  public PlatformViews.OrderResponse userOrder(@PathVariable long id) {
    return PlatformViewFactory.order(fulfillment.detail(id));
  }

  @PostMapping(value = "/user/orders/{id}/actions/{action}")
  @io.swagger.v3.oas.annotations.Operation(operationId = "orderAction")
  public Object orderAction(
      @PathVariable long id,
      @PathVariable String action,
      @Valid @RequestBody PlatformCommands.Action input) {
    return action.equals("verify")
        ? fulfillment.verify(id, input)
        : fulfillment.action(id, action, input);
  }

  @PostMapping(value = "/courier/orders/{id}/actions/{action}")
  @io.swagger.v3.oas.annotations.Operation(operationId = "orderAction_1")
  public Object courierOrderAction(
      @PathVariable long id,
      @PathVariable String action,
      @Valid @RequestBody PlatformCommands.Action input) {
    return action.equals("verify")
        ? fulfillment.verify(id, input)
        : fulfillment.action(id, action, input);
  }

  @PostMapping(value = "/merchant/orders/{id}/actions/{action}")
  @io.swagger.v3.oas.annotations.Operation(operationId = "orderAction_2")
  public Object merchantOrderAction(
      @PathVariable long id,
      @PathVariable String action,
      @Valid @RequestBody PlatformCommands.Action input) {
    return action.equals("verify")
        ? fulfillment.verify(id, input)
        : fulfillment.action(id, action, input);
  }

  @PostMapping("/platform/orders/{id}/reset-pickup")
  public void resetPickup(@PathVariable long id) {
    fulfillment.resetCode(id);
  }

  @PostMapping("/platform/orders/{id}/unassign")
  public Object unassign(@PathVariable long id, @Valid @RequestBody PlatformCommands.Action input) {
    return fulfillment.action(id, "unassign", input);
  }

  @PostMapping("/platform/orders/{id}/reassign")
  public Object reassign(
      @PathVariable long id, @Valid @RequestBody PlatformCommands.Reassign input) {
    return fulfillment.reassign(id, input);
  }

  @PostMapping("/platform/orders/{id}/exception-cancel")
  public Object exceptionCancel(
      @PathVariable long id, @Valid @RequestBody PlatformCommands.ExceptionCancel input) {
    return fulfillment.exceptionCancel(id, input);
  }

  @GetMapping("/courier/pool")
  public Object pool() {
    return fulfillment.pool();
  }

  @PostMapping("/courier/tasks/{id}/claim")
  public void claimTask(@PathVariable long id, @Valid @RequestBody Commands.Version input) {
    fulfillment.claim(id, input.version());
  }
}
